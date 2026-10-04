package com.example.newapp;

interface IPolicyService {

    boolean validatePolicy(String thirdParty);

    String getStoredDeveloper();
}