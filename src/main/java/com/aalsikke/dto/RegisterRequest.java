package com.aalsikke.dto;

/**
 * Mirrors the payload built by register.js:
 * { fullname, dob, email, phone, role, password }
 */
public class RegisterRequest {

    private String fullname;
    private String dob;
    private String email;
    private String phone;
    private String role;
    private String password;

    public RegisterRequest() {}

    public String getFullname() { return fullname; }
    public void setFullname(String fullname) { this.fullname = fullname; }

    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
