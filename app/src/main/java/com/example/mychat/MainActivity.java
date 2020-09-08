package com.example.mychat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Response;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.database.FirebaseListAdapter;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.Reference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private static final int SIGN_IN_REQUEST_CODE = 1;
    private static final int PICK_CONTACT_REQUEST = 2;
    private FirebaseListAdapter<ChatMessage> adapter;
    private StorageReference mStorageRef,file_storage;
    private Intent intent;
    private Uri uri;
    String fileName;

    // 通知
    final private String FCM_API = "https://fcm.googleapis.com/fcm/send";
    final private String serverKey = "key=" + "Firebase伺服器金鑰";
    final private String contentType = "application/json";
    final String TAG = "NOTIFICATION TAG";

    String NOTIFICATION_TITLE;
    String NOTIFICATION_MESSAGE;
    String TOPIC;

    //上傳進度條
    int mprogress;
    ProgressDialog pDialog;
    Handler handler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 處理用戶登入與註冊
        if(FirebaseAuth.getInstance().getCurrentUser() == null) {
            // 開始登入/註冊
            startActivityForResult(
                    AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .build(),
                    SIGN_IN_REQUEST_CODE
            );
        } else {
            // 用戶已經登入，顯示歡迎用戶的Toast
            Toast.makeText(this,
                    "歡迎 " + FirebaseAuth.getInstance()
                            .getCurrentUser()
                            .getDisplayName(),
                    Toast.LENGTH_LONG)
                    .show();

            // 載入聊天室內容
            displayChatMessages();
        }

        // 向Firebase添加聊天訊息
        FloatingActionButton fab = findViewById(R.id.fab);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EditText input = findViewById(R.id.input);
                // 不能傳送空白訊息
                if("".equals(input.getText().toString()))
                    return;

                // 讀取輸入欄位的內容，並將訊息傳送至Firebase數據庫(key-value)
                // 沒有setKey()是因為push()會自動生成一個新的key
                FirebaseDatabase.getInstance()
                        .getReference()
                        .push()
                        .setValue(new ChatMessage(input.getText().toString(),
                                FirebaseAuth.getInstance()
                                        .getCurrentUser()
                                        .getDisplayName())
                        );


                // 設置通知內容
                TOPIC = "/topics/userABC"; //topic has to match what the receiver subscribed to
                NOTIFICATION_TITLE = FirebaseAuth.getInstance().getCurrentUser().getDisplayName(); // 傳送人名字
                NOTIFICATION_MESSAGE = input.getText().toString(); // 傳送的內容

                JSONObject notification = new JSONObject();
                JSONObject notificationBody = new JSONObject();
                try {
                    notificationBody.put("title", NOTIFICATION_TITLE);
                    notificationBody.put("message", NOTIFICATION_MESSAGE);

                    notification.put("to", TOPIC);
                    notification.put("data", notificationBody);
                } catch (JSONException e) {
                    Log.e(TAG, "onCreate: " + e.getMessage() );
                }
                sendNotification(notification); // 傳送通知

                // 清除輸入欄位的內容
                input.setText("");

            }
        });

        // 上傳檔案(開啟選擇檔案)
        mStorageRef = FirebaseStorage.getInstance().getReference();
        FloatingActionButton photo = findViewById(R.id.photo);
        photo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                intent = new Intent();
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.setType("*/*"); // 不限制任何檔案類型
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent,2);
            }
        });

    }


    // 顯示聊天訊息
    private void displayChatMessages() {
        NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE); // 取得系統的通知服務
        notificationManager.cancelAll();  //在聊天畫面就刪除所有通知

        ListView listOfMessages = findViewById(R.id.list_of_messages);

        adapter = new FirebaseListAdapter<ChatMessage>(this, ChatMessage.class,
                R.layout.message, FirebaseDatabase.getInstance().getReference()) {
            @Override
            protected void populateView(View v, ChatMessage model, int position) {
                // 取得在message.xml中的每個TextView
                final TextView messageText = (TextView)v.findViewById(R.id.message_text);
                TextView messageUser = (TextView)v.findViewById(R.id.message_user);
                TextView messageTime = (TextView)v.findViewById(R.id.message_time);

                // 用戶與訊息setText
                messageText.setText(model.getMessageText());
                messageUser.setText(model.getMessageUser());

                // 設定日期時間的格式
                messageTime.setText(DateFormat.format("dd-MM-yyyy (HH:mm:ss)",
                        model.getMessageTime()));

                // 點擊訊息跳轉下載畫面/前往網址所在地
                messageText.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if(messageText.getText().toString().contains("https://")){
                            Uri uri = Uri.parse(messageText.getText().toString());
                            Intent i = new Intent(Intent.ACTION_VIEW,uri);
                            startActivity(i);
                        }else {
                            fileName = messageText.getText().toString().substring(2); // 原本是:📩fileName -> 取出:fileName
                            mStorageRef.child(fileName).getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                @Override
                                public void onSuccess(Uri uri) {
                                    //uri.toString() 是 download URL
                                    Uri u = Uri.parse(uri.toString());
                                    Intent it = new Intent(Intent.ACTION_VIEW, u);
                                    startActivity(it);
                                    Toast.makeText(MainActivity.this, "前往下載畫面", Toast.LENGTH_LONG).show();

                                }
                            });
                        }

                    }
                });

            }
        };

        listOfMessages.setAdapter(adapter);
    }

    // 處理用戶登入/上傳檔案
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        // 用戶登入
        if(requestCode == SIGN_IN_REQUEST_CODE) {
            if(resultCode == RESULT_OK) {
                Toast.makeText(this,
                        "登入成功！",
                        Toast.LENGTH_LONG)
                        .show();
                displayChatMessages();
            } else {
                Toast.makeText(this,
                        "登入失敗，請稍後再試一次！",
                        Toast.LENGTH_LONG)
                        .show();

                //關閉app
                finish();
            }
        }

        // 上傳檔案
        if(requestCode == PICK_CONTACT_REQUEST){
            if(data!=null){
                uri = data.getData();

                // 取得檔名(fileName)
                if (uri.getScheme().equals("file")) {
                    fileName = uri.getLastPathSegment();
                } else {
                    Cursor cursor = null;
                    try {
                        cursor = getContentResolver().query(uri, new String[]{
                                MediaStore.Images.ImageColumns.DISPLAY_NAME
                        }, null, null, null);

                        if (cursor != null && cursor.moveToFirst()) {
                            fileName = cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DISPLAY_NAME));
                        }
                    } finally {

                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                }

                // 確定是否要傳送畫面 + 上傳檔案進度條(水平長條)
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("確定要傳送？\n" + fileName)
                        .setPositiveButton("是", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                pDialog = new ProgressDialog(MainActivity.this);
                                pDialog.setTitle("上傳中...");
                                pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                                pDialog.show();

                                file_storage = mStorageRef.child(fileName); // 放檔名
                                file_storage.putFile(uri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                                    @Override
                                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                        Toast.makeText(MainActivity.this,"上傳成功！",Toast.LENGTH_LONG).show();
                                        String newFileName = "📩" + fileName; // 將檔名前面加上符號使他在app畫面中更好辨識
                                        FirebaseDatabase.getInstance()
                                                .getReference()
                                                .push()
                                                .setValue(new ChatMessage(newFileName,
                                                        FirebaseAuth.getInstance()
                                                                .getCurrentUser()
                                                                .getDisplayName())
                                                );

                                        // 設置通知內容
                                        TOPIC = "/topics/userABC"; //topic has to match what the receiver subscribed to
                                        NOTIFICATION_TITLE = FirebaseAuth.getInstance().getCurrentUser().getDisplayName(); // 傳送人名字
                                        NOTIFICATION_MESSAGE = newFileName; // 傳送的內容

                                        JSONObject notification = new JSONObject();
                                        JSONObject notificationBody = new JSONObject();
                                        try {
                                            notificationBody.put("title", NOTIFICATION_TITLE);
                                            notificationBody.put("message", NOTIFICATION_MESSAGE);

                                            notification.put("to", TOPIC);
                                            notification.put("data", notificationBody);
                                        } catch (JSONException e) {
                                            Log.e(TAG, "onCreate: " + e.getMessage() );
                                        }
                                        sendNotification(notification); // 傳送通知

                                    }
                                }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                                    @SuppressLint("HandlerLeak")
                                    @Override
                                    public void onProgress(@NonNull UploadTask.TaskSnapshot taskSnapshot) {

                                        /*另一種上傳檔案進度條的顯示方式(轉圈)
                                        double progress = (100.0*taskSnapshot.getBytesTransferred()/taskSnapshot.getTotalByteCount());
                                        pDialog.setMessage("Uploaded "+(int)progress+"%");
                                        if((int)progress == 100){
                                            pDialog.setTitle("已上傳");
                                            pDialog.setMessage("Uploaded 100%");
                                        }
                                        */
                                        final double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                                        final Thread t = new Thread(){
                                            @Override
                                            public void run(){
                                                double jumpTime = progress;
                                                if(jumpTime <= 100.0){
                                                    try {
                                                        sleep(200);
                                                        pDialog.setProgress((int)jumpTime);
                                                    } catch (InterruptedException e) {
                                                        // TODO Auto-generated catch block
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }
                                        };
                                        t.start();

                                    }
                                });
                            }
                        }).setNegativeButton("否",null).create()
                        .show();

            }

        }
    }

    // 處理用戶登出(菜單)
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    // 處理菜單選項的點擊事件(登出)
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.menu_sign_out) {
            AuthUI.getInstance().signOut(this)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            Toast.makeText(MainActivity.this,
                                    "您已登出！",
                                    Toast.LENGTH_LONG)
                                    .show();

                            // 關閉事件
                            finish();
                        }
                    });
        }
        return true;
    }

    // 傳送通知 (使用Volley)
    // JsonObjectRequest : To send and receive JSON Object from the Server
    private void sendNotification(JSONObject notification) {
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(FCM_API, notification,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.i(TAG, "onResponse: " + response.toString());
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(MainActivity.this, "Request error", Toast.LENGTH_LONG).show();
                        Log.i(TAG, "onErrorResponse: Didn't work");
                    }
                }){

            // To pass the headers of your request
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                params.put("Authorization", serverKey);
                params.put("Content-Type", contentType);
                return params;
            }
        };
        MySingleton.getInstance(getApplicationContext()).addToRequestQueue(jsonObjectRequest);
    }


}