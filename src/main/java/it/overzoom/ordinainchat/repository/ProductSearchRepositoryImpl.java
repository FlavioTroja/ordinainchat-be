// it/overzoom/ordinainchat/repository/ProductSearchRepositoryImpl.java
package it.overzoom.ordinainchat.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import it.overzoom.ordinainchat.model.Product;
import it.overzoom.ordinainchat.search.ProductSearchCriteria;
import it.overzoom.ordinainchat.type.SortType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Repository
class ProductSearchRepositoryImpl implements ProductSearchRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<Product> search(ProductSearchCriteria c, Pageable pageable) {
        if (c == null)
            c = new ProductSearchCriteria();

        CriteriaBuilder cb = em.getCriteriaBuilder();

        // query dati
        CriteriaQuery<Product> cq = cb.createQuery(Product.class);
        Root<Product> root = cq.from(Product.class);
        List<Predicate> ps = buildPredicates(c, cb, root);

        cq.where(ps.toArray(Predicate[]::new));
        applySort(c.getSortType(), cb, cq, root);

        TypedQuery<Product> tq = em.createQuery(cq);
        tq.setFirstResult((int) pageable.getOffset());
        tq.setMaxResults(pageable.getPageSize());
        List<Product> content = tq.getResultList();

        // query count
        CriteriaQuery<Long> countQ = cb.createQuery(Long.class);
        Root<Product> countRoot = countQ.from(Product.class);
        countQ.select(cb.count(countRoot))
                .where(buildPredicates(c, cb, countRoot).toArray(Predicate[]::new));
        long total = em.createQuery(countQ).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    private List<Predicate> buildPredicates(ProductSearchCriteria c, CriteriaBuilder cb, Root<Product> root) {
        List<Predicate> ps = new ArrayList<>();

        // c.getSearch(): LIKE su name/description (case-insensitive)
        if (c.getSearch() != null && !c.getSearch().isBlank()) {
            String like = "%" + c.getSearch().toLowerCase() + "%";
            ps.add(cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("description")), like)));
        }

        // c.getMaxPrice(): price <= max
        BigDecimal max = c.getMaxPrice();
        if (max != null) {
            ps.add(cb.lessThanOrEqualTo(root.get("price"), max));
        }

        // NB: il tuo Product attuale NON ha campi come onOffer/freshDate.
        // Se li aggiungerai, estendi qui i predicati.

        return ps;
    }

    private void applySort(SortType sortType, CriteriaBuilder cb, CriteriaQuery<Product> cq, Root<Product> root) {
        if (sortType == null)
            return;
        switch (sortType) {
            case PRICE_ASC -> cq.orderBy(cb.asc(root.get("price")));
            case PRICE_DESC -> cq.orderBy(cb.desc(root.get("price")));
            case NAME_ASC -> cq.orderBy(cb.asc(root.get("name")));
            case NAME_DESC -> cq.orderBy(cb.desc(root.get("name")));
            // FRESHNESS_* o OFFER_FIRST richiedono campi che oggi non esistono: ignorali
            default -> {
                /* no-op */ }
        }
    }
}
