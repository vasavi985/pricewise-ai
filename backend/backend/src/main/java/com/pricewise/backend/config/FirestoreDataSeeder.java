package com.pricewise.backend.config;

import com.pricewise.backend.entity.PriceRecord;
import com.pricewise.backend.entity.Product;
import com.pricewise.backend.entity.StoreProduct;
import com.pricewise.backend.repository.PriceRecordRepository;
import com.pricewise.backend.repository.ProductRepository;
import com.pricewise.backend.repository.StoreProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.time.LocalDateTime;

@Component
@Order(1)
@ConditionalOnProperty(name = "pricewise.seeder.enabled", havingValue = "true", matchIfMissing = false)
public class FirestoreDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FirestoreDataSeeder.class);

    private final ProductRepository productRepository;
    private final StoreProductRepository storeProductRepository;
    private final PriceRecordRepository priceRecordRepository;

    public FirestoreDataSeeder(ProductRepository productRepository,
                               StoreProductRepository storeProductRepository,
                               PriceRecordRepository priceRecordRepository) {
        this.productRepository = productRepository;
        this.storeProductRepository = storeProductRepository;
        this.priceRecordRepository = priceRecordRepository;
    }

    @Override
    public void run(String... args) {
        long existingCount = productRepository.count();
        if (existingCount > 0) {
            log.info("Firestore contains {} existing products. Skipping benchmark catalog seed.", existingCount);
            return;
        }

        log.info("Firestore collection is empty. Seeding verified benchmark catalog products and price history...");

        // 1. MacBook Air M2
        seedProductWithHistory(
                1L,
                "MacBook Air M2",
                "Apple",
                "MacBook Air M2 (2022)",
                "Laptops",
                "13.6-inch Liquid Retina Display, 8GB Unified Memory, 256GB SSD Storage, Backlit Magic Keyboard, 1080p FaceTime HD Camera.",
                "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=600&q=80",
                4.8,
                71499.0,
                new double[]{74990.0, 73499.0, 72999.0, 71499.0}
        );

        // 2. iPhone 15
        seedProductWithHistory(
                2L,
                "iPhone 15",
                "Apple",
                "iPhone 15 (128 GB)",
                "Smartphones",
                "Dynamic Island, 48MP Main Camera with 2x Telephoto, USB-C Connectivity, All-day Battery Life, Precision-Engineered Color-Infused Glass.",
                "https://images.unsplash.com/photo-1592750475338-74b7b21085ab?auto=format&fit=crop&w=600&q=80",
                4.7,
                65999.0,
                new double[]{69990.0, 68499.0, 66999.0, 65999.0}
        );

        // 3. Samsung Galaxy S24
        seedProductWithHistory(
                3L,
                "Samsung Galaxy S24",
                "Samsung",
                "Galaxy S24 5G (8GB RAM, 128GB)",
                "Smartphones",
                "Galaxy AI features: Circle to Search, Live Translate, Note Assist. Dynamic AMOLED 2X Display, Snapdragon 8 Gen 3 Processor.",
                "https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?auto=format&fit=crop&w=600&q=80",
                4.6,
                64999.0,
                new double[]{67999.0, 66499.0, 65999.0, 64999.0}
        );

        // 4. Sony WH-1000XM5
        seedProductWithHistory(
                4L,
                "Sony WH-1000XM5 Wireless Headphones",
                "Sony",
                "WH-1000XM5",
                "Audio",
                "Industry Leading Wireless Noise Cancelling Headphones with 8 Microphones, Auto NC Optimizer, 30-Hour Battery Life, Ultra-comfortable fit.",
                "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=600&q=80",
                4.7,
                26990.0,
                new double[]{29990.0, 28490.0, 27990.0, 26990.0}
        );

        log.info("Successfully seeded Firestore with 4 verified benchmark catalog products and initial price histories.");
    }

    private void seedProductWithHistory(Long id, String name, String brand, String model, String category,
                                       String description, String imageUrl, Double rating, Double currentPrice,
                                       double[] historicalPrices) {
        Product p = new Product();
        p.setId(id);
        p.setCanonicalName(name);
        p.setProductName(name);
        p.setBrand(brand);
        p.setModel(model);
        p.setCategory(category);
        p.setDescription(description);
        p.setImageUrl(imageUrl);
        p.setRating(rating);
        p.setFlipkartPrice(currentPrice);
        p.setCreatedAt(LocalDateTime.now().minusDays(30));
        p.setUpdatedAt(LocalDateTime.now());
        p = productRepository.save(p);

        // Benchmark Catalog StoreProduct
        StoreProduct sp = new StoreProduct();
        sp.setId(id);
        sp.setProduct(p);
        sp.setProductId(p.getId());
        sp.setStore("CATALOG");
        sp.setStoreProductId("CAT-" + p.getId());
        sp.setTitle(name + " (Catalog Benchmark)");
        sp.setImageUrl(imageUrl);
        sp.setCurrentPrice(currentPrice);
        sp.setCurrency("INR");
        sp.setAvailability("IN_STOCK");
        sp.setStatus("SAMPLE_DATA");
        sp.setLastCheckedAt(LocalDateTime.now());
        sp = storeProductRepository.save(sp);

        // Historical Price Records for Recharts
        LocalDateTime now = LocalDateTime.now();
        int daysBack = historicalPrices.length * 2;
        for (int i = 0; i < historicalPrices.length; i++) {
            PriceRecord record = new PriceRecord();
            record.setStoreProduct(sp);
            record.setStoreProductId(sp.getId());
            record.setProductId(p.getId());
            record.setStore("CATALOG");
            record.setPrice(historicalPrices[i]);
            record.setCurrency("INR");
            record.setAvailability("IN_STOCK");
            record.setStatus("SAMPLE_DATA");
            record.setSource("INITIAL_CATALOG");
            record.setCheckedAt(now.minusDays(daysBack - (i * 2)));
            priceRecordRepository.save(record);
        }
    }
}
