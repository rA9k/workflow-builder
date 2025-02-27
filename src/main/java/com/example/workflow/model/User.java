package com.example.workflow.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String email;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }
  
    public String getEmail() {
        return email;
    }

    public void setId(Long id) {
        this.id = id;
    }
  
    public void setUsername(String username) {
        this.username = username;
    }
  
    public void setEmail(String email) {
        this.email = email;
    }
}
