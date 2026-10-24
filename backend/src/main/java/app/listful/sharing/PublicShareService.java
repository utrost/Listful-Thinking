package app.listful.sharing;

import app.listful.api.ConflictException;
import app.listful.api.ResourceNotFoundException;
import app.listful.api.ValidationFailedException;
import app.listful.domain.Item;
import app.listful.domain.ListEntity;
import app.listful.domain.RetentionPolicy;
import app.listful.domain.User;
import app.listful.domain.enums.ItemStatus;
import app.listful.domain.enums.ListType;
import app.listful.domain.enums.PublicShareMode;
import app.listful.domain.repository.ItemRepository;
import app.listful.domain.repository.ListRepository;
import app.listful.lists.ListAccessService;
import app.listful.security.TokenHashing;
import app.listful.sharing.dto.GuestClaimRequest;
import app.listful.sharing.dto.GuestClaimControlRequest;
import app.listful.sharing.dto.PublicItemResponse;
import app.listful.sharing.dto.PublicListResponse;
import app.listful.sharing.dto.PublicShareRequest;
import app.listful.sharing.dto.PublicShareTokenResponse;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicShareService {
    private final ListAccessService listAccessService;
    private final ListRepository listRepository;
    private final ItemRepository itemRepository;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public PublicShareService(ListAccessService listAccessService, ListRepository listRepository, ItemRepository itemRepository, Clock clock) {
        this.listAccessService = listAccessService;
        this.listRepository = listRepository;
        this.itemRepository = itemRepository;
        this.clock = clock;
    }

    @Transactional
    public PublicShareTokenResponse createToken(User actor, String listId, PublicShareRequest request) {
        ListEntity list = listAccessService.requireShareableList(actor, listId);
        PublicShareMode mode = request == null || request.mode() == null ? defaultMode(list) : request.mode();
        validateMode(list, mode);
        String token = uniqueToken();
        list.enablePublicShare(TokenHashing.sha256(token), token, mode, clock.instant().plus(RetentionPolicy.PUBLIC_LINK));
        return toTokenResponse(list);
    }

    @Transactional
    public void revokeToken(User actor, String listId) {
        ListEntity list = listAccessService.requireShareableList(actor, listId);
        list.disablePublicShare();
    }

    @Transactional
    public PublicListResponse getPublicList(String token) {
        ListEntity list = publicListByToken(token);
        return new PublicListResponse(
            list.getTitle(),
            list.getDescription(),
            list.getType().name(),
            list.getTargetDate() == null ? null : list.getTargetDate().toString(),
            list.getPublicShareMode().name(),
            itemRepository.findByListId(list.getId()).stream()
                .map(this::toPublicItemResponse)
                .toList(),
            list.getSourceUrl(),
            list.getEventLocation()
        );
    }

    @Transactional
    public PublicItemResponse claim(String token, String itemId, GuestClaimRequest request) {
        ListEntity list = publicListByToken(token);
        if (!claimsEnabled(list)) {
            throw new ValidationFailedException("Guest claiming is not enabled for this public link.");
        }
        int claimed;
        String claimToken = uniqueClaimToken();
        try {
            claimed = itemRepository.claimOpenItem(
                itemId,
                list.getId(),
                request.guestName(),
                TokenHashing.sha256(claimToken),
                ItemStatus.OPEN,
                ItemStatus.CLAIMED
            );
        } catch (RuntimeException ex) {
            if (isSqliteLockContention(ex)) {
                throw new ConflictException("item_already_claimed", "Item is already claimed.");
            }
            throw ex;
        }
        Item item = itemRepository.findById(itemId)
            .filter(candidate -> !candidate.isDeleted() && candidate.getList().getId().equals(list.getId()))
            .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
        if (claimed == 0) {
            throw new ConflictException("item_already_claimed", "Item is already claimed.");
        }
        return toPublicItemResponse(item, true).withClaimToken(claimToken);
    }

    @Transactional
    public void releaseClaim(String token, String itemId, String claimToken) {
        ListEntity list = publicListByToken(token);
        if (claimToken == null || claimToken.length() < 32 ||
                itemRepository.releaseGuestClaim(itemId, list.getId(), TokenHashing.sha256(claimToken)) == 0) {
            throw new ResourceNotFoundException("Claim not found");
        }
    }

    @Transactional
    public PublicItemResponse renameClaim(String token, String itemId, GuestClaimControlRequest request) {
        ListEntity list = publicListByToken(token);
        String name = request.guestName() == null ? "" : request.guestName().trim();
        if (name.isEmpty()) throw new ValidationFailedException("Guest name is required.");
        if (request.claimToken().length() < 32 || itemRepository.renameGuestClaim(itemId, list.getId(),
                TokenHashing.sha256(request.claimToken()), name) == 0) {
            throw new ResourceNotFoundException("Claim not found");
        }
        Item item = itemRepository.findById(itemId).orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
        return toPublicItemResponse(item, true);
    }

    private String uniqueClaimToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ListEntity publicListByToken(String token) {
        String tokenHash = TokenHashing.sha256(token);
        return listRepository.findByShareTokenHash(tokenHash)
            .or(() -> migrateLegacyRawToken(token, tokenHash))
            .filter(list -> list.isPublicShareActive(clock.instant()))
            .orElseThrow(() -> new ResourceNotFoundException("Shared list not found"));
    }

    private java.util.Optional<ListEntity> migrateLegacyRawToken(String token, String tokenHash) {
        if (TokenHashing.isSha256Hash(token)) return java.util.Optional.empty();
        return listRepository.findByShareTokenHash(token)
            .filter(list -> list.isPublicShareActive(clock.instant()))
            .map(list -> {
                list.migratePublicShareTokenHash(tokenHash);
                return list;
            });
    }

    private PublicShareMode defaultMode(ListEntity list) {
        return list.getType() == ListType.WISH ? PublicShareMode.WISH_CLAIM : PublicShareMode.VIEW;
    }

    private void validateMode(ListEntity list, PublicShareMode mode) {
        if (mode == PublicShareMode.WISH_CLAIM && list.getType() != ListType.WISH) {
            throw new ValidationFailedException("Wish claiming mode is only available for wish lists.");
        }
        if (mode == PublicShareMode.SIGNUP && (list.getType() == ListType.WISH || list.getType() == ListType.TRIP || list.getType() == ListType.LOCATION)) {
            throw new ValidationFailedException("Signup mode is only available for task-like lists.");
        }
    }

    private boolean claimsEnabled(ListEntity list) {
        if (list.getPublicShareMode() == PublicShareMode.WISH_CLAIM) {
            return list.getType() == ListType.WISH;
        }
        return list.getPublicShareMode() == PublicShareMode.SIGNUP;
    }

    private boolean isSqliteLockContention(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && (message.contains("SQLITE_LOCKED") || message.contains("database table is locked"))) {
                return true;
            }
        }
        return false;
    }

    private String uniqueToken() {
        String token;
        do {
            byte[] bytes = new byte[32];
            secureRandom.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (listRepository.findByShareTokenHash(TokenHashing.sha256(token)).isPresent());
        return token;
    }

    private PublicShareTokenResponse toTokenResponse(ListEntity list) {
        return new PublicShareTokenResponse(list.getId(), list.isPublicList(), list.getShareToken(), "/s/" + list.getShareToken(), list.getPublicShareMode().name(), list.getPublicShareExpiresAt().toString());
    }

    private PublicItemResponse toPublicItemResponse(Item item) {
        return toPublicItemResponse(item, false);
    }

    private PublicItemResponse toPublicItemResponse(Item item, boolean claimer) {
        return new PublicItemResponse(
            item.getId(),
            item.getName(),
            item.getDescription(),
            item.getUrl(),
            item.getImageUrl(),
            item.getPrice(),
            item.getStatus().name(),
            item.getDueDate() == null ? null : item.getDueDate().toString(),
            item.getQuantity(),
            item.getCategory(),
            claimer || item.getList().getType() != ListType.WISH ? item.getReservedByGuest() : null,
            item.getPriceCurrency(),
            item.getVariantNote(),
            item.getSeller(),
            item.getAvailability(),
            item.getPriceCheckedAt() == null ? null : item.getPriceCheckedAt().toString(),
            item.getSourceUrl(),
            null
        );
    }
}
