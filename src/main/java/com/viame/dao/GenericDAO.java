package com.viame.dao;

import java.util.List;
import java.util.Optional;

/**
 * Generic Data Access Object interface defining standard CRUD operations.
 * Demonstrates Abstraction and Interface Segregation.
 *
 * @param <T> Domain entity type
 * @param <ID> Primary key identifier type
 */
public interface GenericDAO<T, ID> {
    Optional<T> findById(ID id);
    List<T> findAll();
    T save(T entity);
    boolean update(T entity);
    boolean delete(ID id);
}
