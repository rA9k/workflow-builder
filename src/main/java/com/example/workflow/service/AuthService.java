package com.example.workflow.service;

import com.example.workflow.model.User;
import com.example.workflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    // Dummy method for authentication logic; expand as needed.
    public User authenticate(String username, String password) {
        // Implement your authentication logic here.
        return userRepository.findAll().stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst()
                .orElse(null);
    }
}
