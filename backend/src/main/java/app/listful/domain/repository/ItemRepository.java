package app.listful.domain.repository;

import app.listful.domain.Item;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, String> {
    List<Item> findByListId(String listId);
}
