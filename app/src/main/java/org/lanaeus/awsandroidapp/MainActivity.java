package org.lanaeus.awsandroidapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;

import com.amazonaws.amplify.generated.graphql.ListCarsQuery;
import com.amazonaws.amplify.generated.graphql.OnCreateCarSubscription;
import com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall;
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers;
import com.apollographql.apollo.GraphQLCall;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;

import java.util.ArrayList;

import javax.annotation.Nonnull;

public class MainActivity extends AppCompatActivity {

    RecyclerView mRecyclerView;
    MyAdapter myAdapter;

    private ArrayList<ListCarsQuery.Item> mCars;
    private final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mRecyclerView = findViewById(R.id.recycler_view);

        //use Liner layout
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        //connect adapter
        myAdapter = new MyAdapter(this);
        mRecyclerView.setAdapter(myAdapter);

        ClientFactory.init(this);

        FloatingActionButton btnAddCar = findViewById(R.id.btn_addCar);
        btnAddCar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, AddCarActivity.class);
                MainActivity.this.startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        //query list data when we come back to the page/screen
        query();
        subscribe();
    }

    @Override
    protected void onStop() {
        super.onStop();
        subscriptionWatcher.cancel();
    }

    public void query(){

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            Log.d(TAG, "WRITE_EXTERNAL_STORAGE permission not granted! Requesting...");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    2);
        }
        ClientFactory.appSyncClient().query(ListCarsQuery.builder().build())
                .responseFetcher(AppSyncResponseFetchers.CACHE_AND_NETWORK)
                .enqueue(queryCallback);
    }

    private GraphQLCall.Callback<ListCarsQuery.Data> queryCallback = new GraphQLCall.Callback<ListCarsQuery.Data>() {
        @Override
        public void onResponse(@Nonnull Response<ListCarsQuery.Data> response) {
            mCars = new ArrayList<>(response.data().listCars().items());

            Log.i(TAG, "Retrieved list of items: " + mCars.toString());

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    myAdapter.setItems(mCars);
                    myAdapter.notifyDataSetChanged();
                }
            });
        }

        @Override
        public void onFailure(@Nonnull ApolloException e) {
            Log.e(TAG, e.toString());
        }
    };

    private AppSyncSubscriptionCall subscriptionWatcher;

    private void subscribe(){
        OnCreateCarSubscription subscription = OnCreateCarSubscription.builder().build();
        subscriptionWatcher = ClientFactory.appSyncClient().subscribe(subscription);
        subscriptionWatcher.execute(subCallback);
    }
    private AppSyncSubscriptionCall.Callback subCallback = new AppSyncSubscriptionCall.Callback() {
        @Override
        public void onResponse(@Nonnull Response response) {
            Log.i("Response", "Received subscription notification: " + response.data().toString());

            //Update UI with newly added item
            OnCreateCarSubscription.OnCreateCar data = ((OnCreateCarSubscription.Data)response.data()).onCreateCar();
            final ListCarsQuery.Item addedItem = new ListCarsQuery.Item(data.__typename(),data.id(),data.brand(),data.model(),data.description(),data.photo());

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mCars.add(addedItem);
                    myAdapter.notifyItemInserted(mCars.size() - 1);
                }
            });
        }

        @Override
        public void onFailure(@Nonnull ApolloException e) {
            Log.e("Error", e.toString());
        }

        @Override
        public void onCompleted() {
            Log.i("Completed", "Subscription completed");
        }
    };
}
