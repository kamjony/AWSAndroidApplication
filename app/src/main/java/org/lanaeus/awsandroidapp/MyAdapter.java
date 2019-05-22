package org.lanaeus.awsandroidapp;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.amazonaws.amplify.generated.graphql.ListCarsQuery;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MyAdapter extends RecyclerView.Adapter<MyAdapter.ViewHolder> {

    private List<ListCarsQuery.Item> mData = new ArrayList<>();
    private LayoutInflater mInflater;

    MyAdapter(Context context){
        this.mInflater = LayoutInflater.from(context);
    }

    //inflates row from xml when requested
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.recyclerview_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bindData(mData.get(position));
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public void setItems(List<ListCarsQuery.Item> mData) {
        this.mData = mData;
    }

    //stores and recycles view as the user scrolls
    class ViewHolder extends RecyclerView.ViewHolder{
        TextView txt_brand;
        TextView txt_model;
        TextView txt_description;
        ImageView image_view;
        String localUrl;

        ViewHolder(View itemView) {
            super(itemView);

            txt_brand = itemView.findViewById(R.id.txt_brand);
            txt_model = itemView.findViewById(R.id.txt_model);
            txt_description = itemView.findViewById(R.id.txt_description);
            image_view = itemView.findViewById(R.id.image_view);
        }
        void bindData(ListCarsQuery.Item item) {
            txt_brand.setText(item.brand());
            txt_model.setText(item.model());
            txt_description.setText(item.description());

            if (item.photo() != null) {
                if (localUrl == null) {
                    downloadWithTransferUtility(item.photo());
                } else {
                    image_view.setImageBitmap(BitmapFactory.decodeFile(localUrl));
                }
            } else {
                image_view.setImageBitmap(null);
            }
        }

            private void downloadWithTransferUtility (final String photo){
                final String localPath = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/" + photo;

                TransferObserver downloadObserver = ClientFactory.transferUtility().download(photo, new File(localPath));

                //Attach a listener to the oberver to get state update and progress notifications
                downloadObserver.setTransferListener(new TransferListener() {
                    @Override
                    public void onStateChanged(int id, TransferState state) {
                        if (TransferState.COMPLETED == state) {
                            // Handle a completed upload.
                            localUrl = localPath;
                            image_view.setImageBitmap(BitmapFactory.decodeFile(localPath));
                        }
                    }

                    @Override
                    public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                        float percentDonef = ((float) bytesCurrent / (float) bytesTotal) * 100;
                        int percentDone = (int) percentDonef;

                        Log.d("", "   ID:" + id + "   bytesCurrent: " + bytesCurrent + "   bytesTotal: " + bytesTotal + " " + percentDone + "%");

                    }

                    @Override
                    public void onError(int id, Exception ex) {
                        Log.e("", "Unable to download the file.", ex);
                    }
                });
            }


    }
}
