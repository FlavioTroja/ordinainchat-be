package it.overzoom.ordinainchat.controller;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

public abstract class BaseSearchController<T, DTO> {

    @Autowired
    protected MongoTemplate mongoTemplate;

    protected abstract String getCollectionName();

    protected abstract Class<T> getEntityClass();

    protected abstract Function<T, DTO> toDtoMapper();

    protected List<String> getSearchableFields() {
        return List.of();
    }

    @PostMapping("/search")
    public ResponseEntity<Page<DTO>> search(@RequestBody Map<String, Object> request) {
        int page = (int) request.getOrDefault("page", 0);
        int limit = (int) request.getOrDefault("limit", 10);

        Map<String, String> filters = extractMap(request.get("filters"));
        Map<String, String> sortMap = extractMap(request.get("sort"));

        // Lista di criteri (AND)
        List<Criteria> andCriteria = new java.util.ArrayList<>();

        // Applica filtri normali (e &&)
        filters.forEach((key, value) -> {
            if (value != null && !value.isEmpty()) {
                andCriteria.add(
                        Criteria.where(key).regex(Pattern.compile(Pattern.quote(value), Pattern.CASE_INSENSITIVE)));
            }
        });

        // --- Logica search full-text (OR tra i campi, AND con i filtri normali) ---
        String searchText = (String) request.get("search");
        List<String> searchFields = getSearchableFields();
        if (searchText != null && !searchText.isEmpty() && !searchFields.isEmpty()) {
            List<Criteria> orList = searchFields.stream()
                    .map(field -> Criteria.where(field)
                            .regex(Pattern.compile(Pattern.quote(searchText), Pattern.CASE_INSENSITIVE)))
                    .toList();
            andCriteria.add(new Criteria().orOperator(orList.toArray(new Criteria[0])));
        }

        Criteria criteria;
        if (andCriteria.isEmpty()) {
            criteria = new Criteria();
        } else if (andCriteria.size() == 1) {
            criteria = andCriteria.getFirst();
        } else {
            criteria = new Criteria().andOperator(andCriteria.toArray(new Criteria[0]));
        }

        Query query = new Query(criteria);

        Sort sort = buildSort(sortMap);
        if (sort != null) {
            query.with(sort);
        }
        Pageable pageable = PageRequest.of(page, limit);
        query.with(pageable);

        long total = mongoTemplate.count(query, getEntityClass(), getCollectionName());
        List<T> result = mongoTemplate.find(query, getEntityClass(), getCollectionName());
        List<DTO> dtoList = result.stream().map(toDtoMapper()).toList();

        Page<DTO> pageResult = new PageImpl<>(dtoList, pageable, total);
        return ResponseEntity.ok(pageResult);
    }

    private Map<String, String> extractMap(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .filter(e -> e.getKey() instanceof String && e.getValue() instanceof String)
                    .collect(Collectors.toMap(
                            e -> (String) e.getKey(),
                            e -> (String) e.getValue()));
        }
        return Map.of();
    }

    private Sort buildSort(Map<String, String> sortMap) {
        if (sortMap == null || sortMap.isEmpty()) {
            return null;
        }
        List<Sort.Order> orders = sortMap.entrySet().stream()
                .map(entry -> new Sort.Order(
                        "desc".equalsIgnoreCase(entry.getValue()) ? Sort.Direction.DESC : Sort.Direction.ASC,
                        entry.getKey()))
                .toList();
        return Sort.by(orders);
    }
}
