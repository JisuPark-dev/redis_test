package com.study.redis_test.repository;

import com.study.redis_test.entity.ProductPlain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface ProductPlainRepository extends JpaRepository<ProductPlain, Long> {
    
    Optional<ProductPlain> findBySku(String sku);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductPlain p WHERE p.id = :id")
    Optional<ProductPlain> findByIdWithPessimisticLock(@Param("id") Long id);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductPlain p WHERE p.sku = :sku")
    Optional<ProductPlain> findBySkuWithPessimisticLock(@Param("sku") String sku);
    
    boolean existsBySku(String sku);
}