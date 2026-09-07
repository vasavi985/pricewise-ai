package com.pricewise.backend.repository;

import com.pricewise.backend.entity.UserSearchHistory;

import java.util.List;

public interface UserSearchHistoryRepository {

    UserSearchHistory save(UserSearchHistory history);

    List<UserSearchHistory> findByUserId(String userId);

    void deleteAllByUserId(String userId);
}
