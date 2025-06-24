package it.overzoom.ordinainchat.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import it.overzoom.ordinainchat.dto.ProductDTO;
import it.overzoom.ordinainchat.model.Product;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProductMapper {

    ProductDTO toDto(Product product);

    Product toEntity(ProductDTO productDTO);
}
