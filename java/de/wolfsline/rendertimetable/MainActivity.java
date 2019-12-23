package de.wolfsline.rendertimetable;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfArray;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;

public class MainActivity extends AppCompatActivity {

    /**
     * TAG
     */
    public final static String TAG = MainActivity.class.getSimpleName();


    /**
     * UI
     */
    private Button mBtnRemoveWhiteBorder;
    private Button mBtnRemoveWhiteBorderAndFooter;
    private TextView mTextInput;
    private TextView mTextOutput;
    private TextView mTextCurrentFile;


    /**
     * PATH
     */
    private final File mRootFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "PDF"); // Folders have to be created manually


    /**
     * VARIABLES
     */
    int mOffset = 0; // Remove footer: 160 // Be careful
    Thread mThread;

    /**
     * PdfHandler
     */
    private CustomPdfRenderer mPdfRenderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Lock screen rotation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Set view
        setContentView(R.layout.activity_main);

        mBtnRemoveWhiteBorder = findViewById(R.id.main_button_remove_white_borders);
        mBtnRemoveWhiteBorderAndFooter = findViewById(R.id.main_button_remove_white_borders_and_footer);
        mTextInput = findViewById(R.id.main_text_input);
        mTextOutput = findViewById(R.id.main_text_output);
        mTextCurrentFile = findViewById(R.id.main_text_current_file);

        mTextInput.setText("Quelle: " + mRootFolder.getAbsolutePath() + File.separator + "Source");
        mTextOutput.setText("Ziel: " + mRootFolder.getAbsolutePath() + File.separator + "Destination");
        mTextCurrentFile.setText("Aktuelle Datei: -");

        mBtnRemoveWhiteBorder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOffset = 0;
                startThread();
            }
        });

        mBtnRemoveWhiteBorderAndFooter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOffset = 160;
                startThread();
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        try {
            if (mPdfRenderer != null) {
                mPdfRenderer.close();
            }
        } catch (Exception e) {
            //e.printStackTrace();
        }
        if (mThread != null) {
            mThread.interrupt();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void startThread() {
        if (mThread != null) {
            mThread.interrupt();
            try {
                mThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mThread = new Thread(runnable);
        mThread.start();
    }

    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            File sourceFolder = new File(mRootFolder.getAbsolutePath() + File.separator + "Source"); // Folders have to be created manually
            File destFolder = new File(mRootFolder.getAbsolutePath() + File.separator + "Destination"); // Folders have to be created manually
            if (!(sourceFolder.exists() && destFolder.exists())) {
                return;
            }
            long start = System.currentTimeMillis();
            for (final File file : sourceFolder.listFiles()) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (!file.getName().endsWith(".pdf")) {
                    continue;
                }
                Log.d(TAG, "File: " + file.getAbsolutePath());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTextCurrentFile.setText("Aktuelle Datei: " + file.getName());
                    }
                });
                mPdfRenderer = new CustomPdfRenderer(getApplicationContext(), file);
                List<int[]> list = new ArrayList<int[]>();
                for (int index = 0; index < mPdfRenderer.getPageCount(); index++) {
                    Bitmap bm = mPdfRenderer.showPage(index);
                    //Log.d(TAG, "0.0" + " 0.0" + " " + bm.getWidth() / 3 + " " + bm.getHeight() / 3);
                    if (bm != null) {
                        list.add(getBorder(bm));
                    }
                }
                try {
                    if (mPdfRenderer != null) {
                        mPdfRenderer.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "Number of pages : " + list.size());

                String filename = file.getName();
                try {
                    createCroppoxWithBorderValues(file, destFolder, list);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                list.clear();
                Log.d(TAG, "Finish");
            }
            long stop = System.currentTimeMillis();
            final long diff = stop - start;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTextCurrentFile.setText("Dauer: " + diff/1000 + " sek.");
                }
            });
            Log.d(TAG, "Duration: " + diff/1000 + " sec.");
        }
    };

    private void createCroppoxWithBorderValues(File file, File destFolder, List<int[]> list) throws Exception {
        String name = file.getName();
        PdfReader reader = new PdfReader(file.getAbsolutePath());
        PdfStamper stamper = new PdfStamper(reader, new FileOutputStream(destFolder.getAbsolutePath() + File.separator + name));

        Rectangle r = reader.getPageSize(1);
        Log.d(TAG, r.getLeft() + " " + r.getBottom() + " " + r.getRight() + " " + r.getTop());

        // Go through all pages
        int n = reader.getNumberOfPages();
        for (int i = 1; i <= n; i++) {
            int[] border = list.get(i-1);
            PdfDictionary page = reader.getPageN(i);
            page.put(PdfName.CROPBOX, new PdfArray(new float[]{border[0], border[3], border[2], border[1]}));

            stamper.markUsed(page);
        }
        stamper.close();
        reader.close();
    }

    public int[] getBorder(@NonNull Bitmap bmp) {
        int imgHeight = bmp.getHeight();
        int imgWidth  = bmp.getWidth();
        int[] pixels = new int[imgHeight * imgWidth];
        bmp.getPixels(pixels, 0, imgWidth, 0, 0, imgWidth, imgHeight);

        //Detect border LEFT
        int startWidth = 0;
        for(int x = 0; x < imgWidth; x++) {
            if (startWidth == 0) {
                for (int y = 0; y < imgHeight; y++) {
                    if (pixels[imgWidth * y + x] != Color.TRANSPARENT) {
                        startWidth = x;
                        break;
                    }
                }
            } else {
                break;
            }
        }


        //Detect border RIGHT
        int endWidth  = 0;
        for(int x = imgWidth - 1; x >= 0; x--) {
            if (endWidth == 0) {
                for (int y = 0; y < imgHeight - mOffset; y++) {
                    if (pixels[imgWidth * y + x] != Color.TRANSPARENT) {
                        endWidth = x;
                        break;
                    }
                }
            } else {
                break;
            }
        }



        //Detect border TOP
        int startHeight = 0;
        for(int y = 0; y < imgHeight; y++) {
            if (startHeight == 0) {
                for (int x = 0; x < imgWidth; x++) {
                    if (pixels[imgWidth * y + x] != Color.TRANSPARENT) {
                        startHeight = y;
                        break;
                    }
                }
            } else {
                break;
            }
        }



        //Detect border BOTTOM
        int endHeight = 0;
        for(int y = imgHeight - 1 - mOffset; y >= 0; y--) {
            if (endHeight == 0 ) {
                for (int x = 0; x < imgWidth; x++) {
                    if (pixels[imgWidth * y + x] != Color.TRANSPARENT) {
                        endHeight = y;
                        break;
                    }
                }
            } else {
                break;
            }
        }

        // Ratio Bitmap <-> iText PDF 1:3
        int[] intArray = new int[] {startWidth/3, (imgHeight - startHeight)/3 + 2, (endWidth)/3 + 1, (imgHeight - endHeight)/3};

        return intArray;
    }
}
