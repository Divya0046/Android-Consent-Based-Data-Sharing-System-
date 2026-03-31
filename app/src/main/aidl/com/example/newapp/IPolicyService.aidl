package com.example.newapp;

interface IPolicyService {
    boolean validatePolicy(
        String developer,
        String thirdParty,
        String category,
        String purpose
    );
    String getStoredDeveloper();
}