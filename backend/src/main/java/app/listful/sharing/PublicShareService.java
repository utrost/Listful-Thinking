package app.listful.sharing;

import app.listful.api.ConflictException;
import app.listful.api.ResourceNotFoundException;
import app.listful.api.ValidationFailedException;
import app.listful.domain.Item;
import app.listful.domain.ListEntity;
import app.listful.domain.User;
import app.listful.domain.enums.ItemStatus;
import app.listful.domain.enums.ListType;
import app.listful.domain.repository.ItemRepository;
import app.listful.domain.repository.ListRepository;
import app.listful.lists.ListAccessService;
import app.listful.sharing.dto.GuestClaimRequest;
import app.listful.sharing.dto.PublicItemResponse;
import app.listful.sharing.dto.PublicListResponse;
import app.listful.sharing.dto.PublicShareTokenResponse;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicShareService {
    private final ListAccessService listAccessService;
    private final ListRepository listRepository;
    private final ItemRepository itemRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public PublicShareService(ListAccessService listAccessService, ListRepository listRepository, ItemRepository itemRepository) {
        this.listAccessService = listAccessService;
        this.listRepository = listRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional
    public PublicShareTokenResponse createToken(User actor, String listId) {
        ListEntity list = listAccessService.requireOwnedList(actor, listId);
        if (!list.isPublicList() || list.getShareToken() == null) {
            list.enablePublicShare(uniqueToken());
        }
        return toTokenResponse(list);
    }

    @Transactional
    public void revokeToken(User actor, String listId) {
        ListEntity list = listAccessService.requireOwnedList(actor, listId);
        list.disablePublicShare();
    }

    @Transactional(readOnly = true)
    public PublicListResponse getPublicList(String token) {
        ListEntity list = publicListByToken(token);
        return new PublicListResponse(
            list.getTitle(),
            list.getDescription(),
            list.getType().name(),
            list.getTargetDate() == null ? null : list.getTargetDate().toString(),
            itemRepository.findByListId(list.getId()).stream()
                .map(this::toPublicItemResponse)
                .toList()
        );
    }

    @Transactional
    public PublicItemResponse claim(String token, String itemId, GuestClaimRequest request) {
        ListEntity list = publicListByToken(token);
        if (list.getType() != ListType.WISH) {
            throw new ValidationFailedException("Guest claiming is only available for wish lists.");
        }
        Item item = itemRepository.findById(itemId)
            .filter(candidate -> candidate.getList().getId().equals(list.getId()))
            .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
        if (item.getStatus() != ItemStatus.OPEN) {
            throw new ConflictException("item_already_claimed", "Item is already claimed.");
        }
        item.claimForGuest(request.guestName());
        return toPublicItemResponse(item);
    }

    private ListEntity publicListByToken(String token) {
        return listRepository.findByShareToken(token)
            .filter(ListEntity::isPublicList)
            .orElseThrow(() -> new ResourceNotFoundException("Shared list not found"));
    }

    private String uniqueToken() {
        String token;
        do {
            byte[] bytes = new byte[32];
            secureRandom.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (listRepository.findByShareToken(token).isPresent());
        return token;
    }

    private PublicShareTokenResponse toTokenResponse(ListEntity list) {
        return new PublicShareTokenResponse(list.getId(), list.isPublicList(), list.getShareToken(), "/s/" + list.getShareToken());
    }

    private PublicItemResponse toPublicItemResponse(Item item) {
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
            item.getReservedByGuest()
        );
    }
}
