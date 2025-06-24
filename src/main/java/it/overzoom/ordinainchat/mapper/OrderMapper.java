package it.overzoom.ordinainchat.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import it.overzoom.ordinainchat.dto.OrderDTO;
import it.overzoom.ordinainchat.model.Order;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

    OrderDTO toDto(Order order);

    Order toEntity(OrderDTO orderDTO);
}
