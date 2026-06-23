package org.example.service;

public interface IUserService {
    void blockUser(String targetUser, boolean block) throws Exception;
    void changeRole(String targetUser, String role) throws Exception;
    void createUser(String targetUser, String password, String role) throws Exception;
}
