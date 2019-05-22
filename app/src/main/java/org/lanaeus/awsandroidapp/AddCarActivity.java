package org.lanaeus.awsandroidapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.amazonaws.amplify.generated.graphql.CreateCarMutation;
import com.amazonaws.amplify.generated.graphql.ListCarsQuery;
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient;
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.apollographql.apollo.GraphQLCall;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import type.CreateCarInput;

public class AddCarActivity extends AppCompatActivity {

    private static final String TAG = AddCarActivity.class.getSimpleName();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_car);
        
        Button btnAddItem = findViewById(R.id.btn_save);
        btnAddItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                uploadAndSave();
            }
        });

        Button btnAddPhoto = findViewById(R.id.btn_add_photo);
        btnAddPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                choosePhoto();
            }
        });
    }

    private void save() {
        CreateCarInput input = getCreateCarInput();

        CreateCarMutation addCarMutation = CreateCarMutation.builder()
                .input(input).build();

        
        ClientFactory.appSyncClient().mutate(addCarMutation).refetchQueries(ListCarsQuery.builder().build()).enqueue(mutateCallback);

        //Enables offline support via an optimistic update
        addCarOffline(input);

    }

    private void addCarOffline(CreateCarInput input) {

        final CreateCarMutation.CreateCar expected =
                new CreateCarMutation.CreateCar("Car",
                        UUID.randomUUID().toString(),
                        input.brand(),
                        input.model(),
                        input.description(),
                        input.photo());

        final AWSAppSyncClient awsAppSyncClient = ClientFactory.appSyncClient();
        final ListCarsQuery listCarsQuery = ListCarsQuery.builder().build();

        awsAppSyncClient.query(listCarsQuery).responseFetcher(AppSyncResponseFetchers.CACHE_ONLY)
                .enqueue(new GraphQLCall.Callback<ListCarsQuery.Data>() {
                    @Override
                    public void onResponse(@Nonnull Response<ListCarsQuery.Data> response) {
                        List<ListCarsQuery.Item> items = new ArrayList<>();
                        if(response.data() != null) {
                            items.addAll(response.data().listCars().items());
                        }
                        items.add(new ListCarsQuery.Item(expected.__typename(),
                                expected.id(), expected.brand(), expected.model(), expected.description(), expected.photo()));
                        ListCarsQuery.Data data = new ListCarsQuery.Data(new ListCarsQuery.ListCars("ModelCarConnection", items, null));
                        awsAppSyncClient.getStore().write(listCarsQuery, data).enqueue(null);
                        Log.d(TAG, "Offline item saved locally successfully");

                        finishIfOffline();
                    }

                    @Override
                    public void onFailure(@Nonnull ApolloException e) {
                        Log.e(TAG, "Failed to update event list", e);
                    }
                });
    }

    private void finishIfOffline() {
        //close the add activity when offline otherwise allow callback to close
        ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

        if(!isConnected){
            Log.d(TAG, "App is offline. Returning to MainActivity");
            finish();
        }
    }

    private GraphQLCall.Callback<CreateCarMutation.Data> mutateCallback = new GraphQLCall.Callback<CreateCarMutation.Data>() {
        @Override
        public void onResponse(@Nonnull Response<CreateCarMutation.Data> response) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(AddCarActivity.this,"Added Car" , Toast.LENGTH_SHORT).show();
                    AddCarActivity.this.finish();
                }
            });
        }

        @Override
        public void onFailure(@Nonnull final ApolloException e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.e("", "Failed to perform AddCarMutation", e);
                    Toast.makeText(AddCarActivity.this, "Failed", Toast.LENGTH_SHORT).show();
                    AddCarActivity.this.finish();
                }
            });
        }
    };

    //Photo Selector
    private static int RESULT_LOAD_IMAGE = 1;
    private String photoPath;

    public void choosePhoto(){
        Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(i, RESULT_LOAD_IMAGE);


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RESULT_LOAD_IMAGE && requestCode == RESULT_OK && null != data){
            Uri selectedImage = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};
            Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            cursor.close();

            //String picturePath contains path of the image selected
            photoPath = picturePath;
        }

    }

    //codes for TransferUtility to upload photos
    private String getS3Key(String localPath){
        return "public/" + new File(localPath).getName();
    }

    public void uploadWithTransferUtility(String localPath){
        String key = getS3Key(localPath);

        Log.d(TAG, "Uploading picture from: " + localPath + " to " + key);

        TransferObserver uploadObserver = ClientFactory.transferUtility().upload(key, new File(localPath));

        // Attach a listener to the observer to get state update and progress notifications
        uploadObserver.setTransferListener(new TransferListener() {
            @Override
            public void onStateChanged(int id, TransferState state) {
                if (TransferState.COMPLETED == state){
                    //handle the completed upload
                    Log.d(TAG, "Upload completed");
                    Toast.makeText(AddCarActivity.this,"PhotoUploaded", Toast.LENGTH_SHORT).show();
                    //save the rest and send the mutation to server
                    save();
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                float percentDonef = ((float) bytesCurrent / (float) bytesTotal) * 100;
                int percentDone = (int) percentDonef;

                Log.d(TAG, "ID: " + id + " bytesCurrent: " + bytesCurrent
                        + " bytesTotal: " + bytesTotal + " " + percentDone + "%");
            }

            @Override
            public void onError(int id, Exception ex) {
                                //handle errors
                Log.e(TAG, "failed to upload photo. ", ex);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(AddCarActivity.this,"Failed to Upload photo. ", Toast.LENGTH_LONG).show();
                    }
                });

            }
        });

    }

    private CreateCarInput getCreateCarInput(){
        final String brand = ((EditText) findViewById(R.id.editTxt_brand)).getText().toString();
        final String model = ((EditText) findViewById(R.id.editTxt_model)).getText().toString();
        final String description = ((EditText) findViewById(R.id.editText_description)).getText().toString();

        if (photoPath != null && !photoPath.isEmpty()){
            return CreateCarInput.builder().brand(brand).model(model).description(description).photo(getS3Key(photoPath)).build();

        } else{
            return CreateCarInput.builder().brand(brand).model(model).description(description).build();
        }
    }

    private void uploadAndSave() {
        if (photoPath != null) {
            //check for permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                //no permission
                Log.d(TAG, "READ_EXTERNAL_STORAGE permission not granted! Requesting...");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);

            }

            //upload photo. save will be made on successful callbacks
            uploadWithTransferUtility(photoPath);
        } else {
                save();
        }
    }
}
