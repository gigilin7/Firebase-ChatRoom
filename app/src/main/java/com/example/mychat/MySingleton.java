package com.example.mychat;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

// Volley : a HTTP library that makes networking for Android apps easier and, most importantly, faster.
// Volley GET/POST請求的基本步驟：
// 1.創建一個RequestQueue對象
// 2.創建一個StringRequest/JsonObjectRequest/JsonArrayRequest對象
// 3.將StringRequest/JsonObjectRequest/JsonArrayRequest對象添加到RequestQueue裡面

public class MySingleton {
    private  static MySingleton instance;
    private RequestQueue requestQueue;
    private Context ctx;

    private MySingleton(Context context) {
        ctx = context;
        requestQueue = getRequestQueue();
    }

    // singleton:只有一個類別，其中提供存取自己物件的方法，確保整個系統只有實例化一個物件(只有一個實例存在)
    // 在多執行緒的環境下，為了避免資源同時競爭而導致產生多個實例的情況，加上同步（synchronized）機制
    public static synchronized MySingleton getInstance(Context context) {
        if (instance == null) {
            instance = new MySingleton(context);
        }
        return instance;
    }

    public RequestQueue getRequestQueue() {
        if (requestQueue == null) {
            // getApplicationContext() is key, it keeps you from leaking the
            // Activity or BroadcastReceiver if someone passes one in.
            requestQueue = Volley.newRequestQueue(ctx.getApplicationContext());
        }
        return requestQueue;
    }

    // <T>:泛型(不用強制轉型，使用型態不對時，在編譯時期會報錯)
    // T(type)表示具體的一個java類型(可以代表很多型態，如Integer...)
    public <T> void addToRequestQueue(Request<T> req) {
        getRequestQueue().add(req);
    }
}
