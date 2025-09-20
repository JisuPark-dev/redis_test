package com.study.redis_test.service;

import com.study.redis_test.dto.ProductCreateRequest;
import com.study.redis_test.dto.ProductResponse;
import com.study.redis_test.dto.ProductUpdateRequest;
import com.study.redis_test.dto.ConcurrentTestRequest;
import com.study.redis_test.dto.ConcurrentTestResult;
import com.study.redis_test.entity.Product;
import com.study.redis_test.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {
    
    private final ProductRepository productRepository;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationContext applicationContext;
    
    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new IllegalArgumentException("이미 존재하는 SKU입니다: " + request.getSku());
        }
        
        Product product = Product.builder()
                .sku(request.getSku())
                .name(request.getName())
                .priceKrw(request.getPriceKrw())
                .stockQty(request.getStockQty())
                .build();
                
        Product savedProduct = productRepository.save(product);
        return ProductResponse.from(savedProduct);
    }
    
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + id));
        return ProductResponse.from(product);
    }
    
    public ProductResponse getProductBySku(String sku) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + sku));
        return ProductResponse.from(product);
    }
    
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public ProductResponse updateProduct(Long id, ProductUpdateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + id));
        
        if (!product.getVersion().equals(request.getVersion())) {
            throw new OptimisticLockingFailureException("상품 정보가 다른 사용자에 의해 변경되었습니다. 다시 시도해주세요.");
        }
        
        product.setName(request.getName());
        product.setPriceKrw(request.getPriceKrw());
        product.setStockQty(request.getStockQty());
        
        Product updatedProduct = productRepository.save(product);
        return ProductResponse.from(updatedProduct);
    }
    
    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다: " + id);
        }
        productRepository.deleteById(id);
    }
    
    @Transactional
    @Retryable(
        value = OptimisticLockingFailureException.class,
        maxAttempts = 10,
        backoff = @Backoff(delay = 50)
    )
    public ProductResponse updateStock(Long id, Integer quantity) {
        log.debug("Updating stock for product {} with quantity {}", id, quantity);
        Product product = productRepository.findByIdWithOptimisticLock(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + id));
        
        int newStock = product.getStockQty() + quantity;
        if (newStock < 0) {
            throw new IllegalArgumentException("재고가 부족합니다. 현재 재고: " + product.getStockQty());
        }
        
        product.setStockQty(newStock);
        Product updatedProduct = productRepository.save(product);
        return ProductResponse.from(updatedProduct);
    }
    
    public ConcurrentTestResult testConcurrentStockUpdate(Long id, ConcurrentTestRequest request) {
        long startTime = System.currentTimeMillis();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger retryCount = new AtomicInteger(0);
        List<String> errors = new ArrayList<>();
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < request.getConcurrentCount(); i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    ProductService productService = applicationContext.getBean(ProductService.class);
                    productService.updateStock(id, request.getQuantity());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    synchronized (errors) {
                        errors.add("오류: " + e.getMessage());
                    }
                }
            }, executor);
            futures.add(future);
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        
        long endTime = System.currentTimeMillis();
        
        Product finalProduct = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + id));
        
        return ConcurrentTestResult.builder()
                .totalRequests(request.getConcurrentCount())
                .successCount(successCount.get())
                .failCount(failCount.get())
                .totalExecutionTimeMs(endTime - startTime)
                .finalStockQty(finalProduct.getStockQty())
                .errors(errors)
                .lockType("OPTIMISTIC_WITH_RETRY")
                .build();
    }
    
    public ConcurrentTestResult testTrueConcurrentStockUpdate(Long id, ConcurrentTestRequest request) {
        long startTime = System.currentTimeMillis();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> errors = new ArrayList<>();
        
        // 요청 수만큼 스레드 생성 (최대 1000개로 제한)
        int threadCount = Math.min(request.getConcurrentCount(), 1000);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // CountDownLatch로 모든 스레드가 동시에 시작하도록 제어
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // 모든 스레드가 여기서 대기
                    startLatch.await();
                    
                    // 동시에 실행
                    ProductService productService = applicationContext.getBean(ProductService.class);
                    productService.updateStock(id, request.getQuantity());
                    successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failCount.incrementAndGet();
                    synchronized (errors) {
                        errors.add("인터럽트 오류: " + e.getMessage());
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    synchronized (errors) {
                        errors.add("오류: " + e.getMessage());
                    }
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        // 모든 스레드가 준비되면 동시에 시작
        startLatch.countDown();
        
        try {
            // 모든 스레드가 완료될 때까지 대기
            endLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("대기 중 인터럽트 발생", e);
        }
        
        executor.shutdown();
        
        long endTime = System.currentTimeMillis();
        
        Product finalProduct = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + id));
        
        return ConcurrentTestResult.builder()
                .totalRequests(threadCount)
                .successCount(successCount.get())
                .failCount(failCount.get())
                .totalExecutionTimeMs(endTime - startTime)
                .finalStockQty(finalProduct.getStockQty())
                .errors(errors)
                .lockType("OPTIMISTIC_WITH_RETRY_TRUE_CONCURRENT")
                .build();
    }
}