package com.Zhaang1.Twiniverse;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SharedViewModel extends ViewModel {
    private final MutableLiveData<Boolean> loginResult = new MutableLiveData<>();
    private final MutableLiveData<String> accountResult = new MutableLiveData<>();

    public void setLoginResult(boolean result) {
        loginResult.setValue(result);
    }

    public void setAccount(String account) {
        accountResult.setValue(account);
    }

    public MutableLiveData<Boolean> getLoginResult() {
        return loginResult;
    }

    public MutableLiveData<String> getAccount() {
        return accountResult;
    }
}