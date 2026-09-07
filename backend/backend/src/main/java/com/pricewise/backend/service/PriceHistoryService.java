package com.pricewise.backend.service;

import com.pricewise.backend.dto.PriceHistoryDTO;
import com.pricewise.backend.dto.PricePointDTO;
import com.pricewise.backend.entity.PriceRecord;
import com.pricewise.backend.entity.Product;
import com.pricewise.backend.repository.PriceRecordRepository;
import com.pricewise.backend.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class PriceHistoryService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd");

    private final ProductRepository productRepository;
    private final PriceRecordRepository priceRecordRepository;

    public PriceHistoryService(ProductRepository productRepository, PriceRecordRepository priceRecordRepository) {
        this.productRepository = productRepository;
        this.priceRecordRepository = priceRecordRepository;
    }

    public PriceHistoryDTO getHistory(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with ID: " + productId));

        List<PriceRecord> records = priceRecordRepository.findByProductIdOrderByCheckedAtAsc(productId);

        PriceHistoryDTO dto = new PriceHistoryDTO();
        dto.setProductId(productId);
        dto.setProductName(product.getCanonicalName() != null ? product.getCanonicalName() : product.getProductName());
        dto.setTotalRecords(records.size());

        if (records.isEmpty()) {
            dto.setPriceTrend("INSUFFICIENT_DATA");
            return dto;
        }

        Double lowest = Double.MAX_VALUE;
        Double highest = Double.MIN_VALUE;
        double sum = 0;
        int validCount = 0;
        List<PricePointDTO> points = new ArrayList<>();
        Map<String, List<PricePointDTO>> storeSeries = new HashMap<>();
        LocalDateTime lastCheckedAt = null;
        Double currentLowest = null;

        for (PriceRecord pr : records) {
            if (pr.getPrice() == null || pr.getPrice() <= 0) {
                continue; // Skip invalid, failed, or zero price entries
            }

            String store = pr.getStoreProduct() != null ? pr.getStoreProduct().getStore() : pr.getStore();
            double price = pr.getPrice();

            if (price < lowest) lowest = price;
            if (price > highest) highest = price;
            sum += price;
            validCount++;
            currentLowest = price; // Latest valid price point

            if (pr.getCheckedAt() != null) {
                if (lastCheckedAt == null || pr.getCheckedAt().isAfter(lastCheckedAt)) {
                    lastCheckedAt = pr.getCheckedAt();
                }
            }

            String status = pr.getStatus() != null ? pr.getStatus() : (pr.getStoreProduct() != null ? pr.getStoreProduct().getStatus() : "SAMPLE_DATA");

            PricePointDTO pt = new PricePointDTO(
                    pr.getCheckedAt() != null ? pr.getCheckedAt().format(DATE_FMT) : "Recent",
                    pr.getCheckedAt(),
                    store,
                    price,
                    pr.getCurrency(),
                    pr.getAvailability(),
                    pr.getSource(),
                    status
            );

            points.add(pt);
            if (store != null) {
                storeSeries.computeIfAbsent(store, k -> new ArrayList<>()).add(pt);
            }
        }

        if (validCount == 0) {
            dto.setPriceTrend("INSUFFICIENT_DATA");
            return dto;
        }

        dto.setLowestRecorded(lowest);
        dto.setHighestRecorded(highest);
        dto.setAverageRecorded(Math.round((sum / validCount) * 100.0) / 100.0);
        dto.setCurrentLowest(currentLowest);
        dto.setLastChecked(lastCheckedAt);
        dto.setTotalRecords(validCount);
        dto.setPoints(points);
        dto.setStoreSeries(storeSeries);

        // Trend calculation
        if (records.size() >= 2) {
            double first = records.get(0).getPrice();
            double last = records.get(records.size() - 1).getPrice();
            if (last < first) {
                dto.setPriceTrend("FALLING");
            } else if (last > first) {
                dto.setPriceTrend("RISING");
            } else {
                dto.setPriceTrend("STABLE");
            }
        } else {
            dto.setPriceTrend("STABLE");
        }

        return dto;
    }
}
