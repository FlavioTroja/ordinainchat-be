// src/main/java/it/overzoom/ordinainchat/repository/ProductSearchRepositoryImpl.java
package it.overzoom.ordinainchat.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import jakarta.persistence.criteria.Expression;
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

        // data
        CriteriaQuery<Product> cq = cb.createQuery(Product.class);
        Root<Product> root = cq.from(Product.class);
        List<Predicate> ps = buildPredicates(c, cb, root);
        cq.where(ps.toArray(Predicate[]::new));
        applySort(c.getSortType(), cb, cq, root);

        TypedQuery<Product> tq = em.createQuery(cq);
        tq.setFirstResult((int) pageable.getOffset());
        tq.setMaxResults(pageable.getPageSize());
        List<Product> content = tq.getResultList();

        // count
        CriteriaQuery<Long> countQ = cb.createQuery(Long.class);
        Root<Product> countRoot = countQ.from(Product.class);
        countQ.select(cb.count(countRoot))
                .where(buildPredicates(c, cb, countRoot).toArray(Predicate[]::new));
        long total = em.createQuery(countQ).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    private List<Predicate> buildPredicates(ProductSearchCriteria c, CriteriaBuilder cb, Root<Product> root) {
        List<Predicate> ps = new ArrayList<>();

        // full-text semplice su name/description
        if (c.getSearch() != null && !c.getSearch().isBlank()) {
            String like = "%" + c.getSearch().toLowerCase() + "%";
            ps.add(cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("description")), like)));
        }

        // solo in offerta
        if (Boolean.TRUE.equals(c.getOnlyOnOffer())) {
            ps.add(cb.isTrue(root.get("onOffer")));
        }

        // prezzo massimo
        BigDecimal max = c.getMaxPrice();
        if (max != null) {
            ps.add(cb.lessThanOrEqualTo(root.get("price"), max));
        }

        // specie richieste: match su name IN (case-insensitive)
        if (c.getItems() != null && !c.getItems().isEmpty()) {
            List<String> itemsLower = c.getItems().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> s.toLowerCase())
                    .toList();
            if (!itemsLower.isEmpty()) {
                Expression<String> nameLower = cb.lower(root.get("name"));
                ps.add(nameLower.in(itemsLower));
            }
        }

        // includere preparati/marinati
        if (Boolean.FALSE.equals(c.getIncludePrepared())) {
            // escludi prepared=true (lascia passare null o false)
            ps.add(cb.or(cb.isNull(root.get("prepared")), cb.isFalse(root.get("prepared"))));
        }
        // se TRUE o null => non filtriamo

        // freshFromDate: priorità a catch_date; se null, fallback su created_at (DATE)
        LocalDate from = c.getFreshFromDate();
        if (from != null) {
            // date(created_at) >= from
            Expression<LocalDate> createdDate = cb.function("date", LocalDate.class, root.get("createdAt"));
            Predicate pCatch = cb.and(cb.isNotNull(root.get("catchDate")),
                    cb.greaterThanOrEqualTo(root.get("catchDate"), from));
            Predicate pCreated = cb.and(cb.isNull(root.get("catchDate")),
                    cb.greaterThanOrEqualTo(createdDate, from));
            ps.add(cb.or(pCatch, pCreated));
        }

        // filtro per freschezza
        if (c.getFreshness() != null) {
            ps.add(cb.equal(root.get("freshness"), c.getFreshness()));
        }

        // filtro per freshFromDate (catchDate >= freshFromDate)
        if (c.getFreshFromDate() != null) {
            ps.add(cb.greaterThanOrEqualTo(root.get("catchDate"), c.getFreshFromDate()));
        }

        // minQuantityKg (se hai il campo)
        if (c.getMinQuantityKg() != null) {
            ps.add(cb.greaterThanOrEqualTo(root.get("quantityKg"), c.getMinQuantityKg()));
        }

        return ps;
    }

    private void applySort(SortType sort, CriteriaBuilder cb, CriteriaQuery<Product> cq, Root<Product> root) {
        if (sort == null)
            return;
        switch (sort) {
            case PRICE_ASC -> cq.orderBy(cb.asc(root.get("price")));
            case PRICE_DESC -> cq.orderBy(cb.desc(root.get("price")));
            case NAME_ASC -> cq.orderBy(cb.asc(root.get("name")));
            case NAME_DESC -> cq.orderBy(cb.desc(root.get("name")));
            case FRESHNESS_DESC -> cq.orderBy(
                    cb.desc(root.get("catchDate")),
                    cb.desc(cb.function("date", java.time.LocalDate.class, root.get("createdAt"))));
            case FRESHNESS_ASC -> cq.orderBy(
                    cb.asc(root.get("catchDate")),
                    cb.asc(cb.function("date", java.time.LocalDate.class, root.get("createdAt"))));
            case OFFER_FIRST -> cq.orderBy(cb.desc(root.get("onOffer")), cb.asc(root.get("price")));
            default -> {
                /* no-op */ }
        }
    }
}
