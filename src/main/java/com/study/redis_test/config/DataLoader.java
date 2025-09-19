package com.study.redis_test.config;

import com.study.redis_test.entity.Product;
import com.study.redis_test.entity.ProductPlain;
import com.study.redis_test.repository.ProductPlainRepository;
import com.study.redis_test.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements ApplicationRunner {
    
    private final ProductRepository productRepository;
    private final ProductPlainRepository productPlainRepository;
    
    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        initializeTestData();
    }
    
    private void initializeTestData() {
        // 낙관적 락용 상품 데이터 (product_optlock 테이블)
        if (!productRepository.existsBySku("OPT-001")) {
            Product optimisticProduct = Product.builder()
                    .sku("OPT-001")
                    .name("낙관적 락 테스트 상품")
                    .priceKrw(10000L)
                    .stockQty(1)  // 재고 1개로 시작
                    .build();
            productRepository.save(optimisticProduct);
            log.info("낙관적 락 테스트 상품 생성: {}", optimisticProduct.getSku());
        }
        
        // 비관적 락용 상품 데이터 (product_plain 테이블)
        if (!productPlainRepository.existsBySku("PES-001")) {
            ProductPlain pessimisticProduct = ProductPlain.builder()
                    .sku("PES-001")
                    .name("비관적 락 테스트 상품")
                    .priceKrw(20000L)
                    .stockQty(1)  // 재고 1개로 시작
                    .build();
            productPlainRepository.save(pessimisticProduct);
            log.info("비관적 락 테스트 상품 생성: {}", pessimisticProduct.getSku());
        }
        
        // Redis 분산락용 상품 데이터 (product_plain 테이블, 다른 SKU)
        if (!productPlainRepository.existsBySku("REDIS-001")) {
            ProductPlain redisProduct = ProductPlain.builder()
                    .sku("REDIS-001")
                    .name("Redis 분산락 테스트 상품")
                    .priceKrw(30000L)
                    .stockQty(1)  // 재고 1개로 시작
                    .build();
            productPlainRepository.save(redisProduct);
            log.info("Redis 분산락 테스트 상품 생성: {}", redisProduct.getSku());
        }
        
        // 락 없는 테스트용 상품 (일반적인 race condition 확인용)
        if (!productPlainRepository.existsBySku("NO-LOCK-001")) {
            ProductPlain noLockProduct = ProductPlain.builder()
                    .sku("NO-LOCK-001")
                    .name("락 없는 테스트 상품")
                    .priceKrw(5000L)
                    .stockQty(5)  // 재고 5개로 시작
                    .build();
            productPlainRepository.save(noLockProduct);
            log.info("락 없는 테스트 상품 생성: {}", noLockProduct.getSku());
        }
        
        log.info("테스트 데이터 초기화 완료");
    }
}