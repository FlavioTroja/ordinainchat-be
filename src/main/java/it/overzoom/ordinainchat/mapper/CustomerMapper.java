package it.overzoom.ordinainchat.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import it.overzoom.ordinainchat.dto.CustomerDTO;
import it.overzoom.ordinainchat.model.Customer;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CustomerMapper {

    CustomerDTO toDto(Customer customer);

    Customer toEntity(CustomerDTO customerDTO);
}
