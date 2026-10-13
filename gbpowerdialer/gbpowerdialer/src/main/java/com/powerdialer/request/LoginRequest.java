package com.powerdialer.request;

import java.util.Objects;

public class LoginRequest {

  private Credentials credentials;

  public Credentials getCredentials() {
    return credentials;
  }

  public void setCredentials(Credentials credentials) {
    this.credentials = credentials;
  }

  public static class Credentials {

    private String username;
    private String password;

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Credentials that = (Credentials) o;
      return Objects.equals(username, that.username) &&
        Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
      return Objects.hash(username, password);
    }

    @Override
    public String toString() {
      return "Credentials{" +
        "username='" + username + '\'' +
        ", password='" + password + '\'' +
        '}';
    }
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LoginRequest that = (LoginRequest) o;
    return Objects.equals(credentials, that.credentials);
  }

  @Override
  public int hashCode() {
    return Objects.hash(credentials);
  }

  @Override
  public String toString() {
    return "LoginRequest{" +
      "credentials=" + credentials +
      '}';
  }
}
