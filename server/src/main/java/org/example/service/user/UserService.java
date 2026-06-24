package org.example.service.user;

import java.util.Map;

public interface UserService {
    void blockUser(String targetUser, boolean block) throws Exception;
    void changeRole(String targetUser, String role) throws Exception;
    void createUser(String targetUser, String password, String role) throws Exception;
    void deleteUser(String targetUser) throws Exception;
    Map<String, Object> searchUsers(String query, int limit, int offset) throws Exception;
}
