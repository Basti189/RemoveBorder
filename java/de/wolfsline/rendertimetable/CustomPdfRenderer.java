package de.wolfsline.rendertimetable;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public class CustomPdfRenderer {

    // https://codeday.me/jp/qa/20190329/491450.html

    private final String TAG = getClass().getSimpleName();

    private Context mContext;

    /**
     * Key string for saving the state of current page index.
     */
    private final static String STATE_CURRENT_PAGE_INDEX = "current_page_index";

    /**
     * The File of the PDF.
     */
    public File mFile;

    /**
     * File descriptor of the PDF.
     */
    private ParcelFileDescriptor mFileDescriptor;

    /**
     * {@link PdfRenderer} to render the PDF.
     */
    private PdfRenderer mPdfRenderer;

    /**
     * Page that is currently shown on the screen.
     */
    private PdfRenderer.Page mCurrentPage;

    /**
     * Actual Bitmap
     */
    private Bitmap bitmap;

    /**
     * Scale of the Page
     */
    private int mScale = 3;

    /**
     * If the page is inverted
     */
    private boolean mIsInvert = false;

    public final static int FIRST_PAGE = 0;

    public CustomPdfRenderer(Context context, File file) {
        mContext = context;
        mFile = file;

        FileManager.deleteCache(mContext);

        try {
            openRenderer(mContext);
        } catch (Exception e) {
            String msg = e.getMessage();
            if(msg.contains("file not in PDF format or corrupted")) {
                Toast.makeText(mContext, "Datei ist beschädigt und kann nicht geöffnet werden!", Toast.LENGTH_LONG).show();
            }
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            closeRenderer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets up a {@link PdfRenderer} and related resources.
     */
    private void openRenderer(Context context) throws Exception {
        mFileDescriptor = ParcelFileDescriptor.open(mFile, ParcelFileDescriptor.MODE_READ_ONLY);
        // This is the PdfRenderer we use to render the PDF.
        if (mFileDescriptor != null) {
            mPdfRenderer = new PdfRenderer(mFileDescriptor);
        }
    }

    /**
     * Closes the {@link PdfRenderer} and related resources.
     *
     * @throws IOException When the PDF file cannot be closed.
     */
    private void closeRenderer() throws IOException {
        if (null != mCurrentPage) {
            mCurrentPage.close();
            mCurrentPage = null;
        }
        if (null != mPdfRenderer) {
            mPdfRenderer.close();
        }
        if (null != mFileDescriptor) {
            mFileDescriptor.close();
        }
    }

    /**
     * Zoom level for zoom matrix depends on screen density (dpiAdjustedZoomLevel), but width and height of bitmap depends only on pixel size and don't depend on DPI
     * Shows the specified page of PDF to the screen.
     *
     * @param index The page index.
     */
    public Bitmap showPage(int index) {
        if (index < 0) {
            index = 0;
        }
        if (mPdfRenderer == null) {
            return null;
        }
        if (mPdfRenderer.getPageCount() <= index) {
            index = mPdfRenderer.getPageCount() - 1;
        }

        if (mCurrentPage != null && mCurrentPage.getIndex() == index) {
            if (mIsInvert) {
                return BitmapManager.invert(bitmap);
            }
            return bitmap;
        }

        // Make sure to close the current page before opening another one.
        if (null != mCurrentPage) {
            mCurrentPage.close();
        }
        // Use `openPage` to open a specific page in PDF.
        mCurrentPage = mPdfRenderer.openPage(index);

        bitmap = Bitmap.createBitmap(mCurrentPage.getWidth() * mScale, mCurrentPage.getHeight() * mScale, Bitmap.Config.ARGB_8888);

        // Here, we render the page onto the Bitmap.
        // To render a portion of the page, use the second and third parameter. Pass nulls to get
        // the default result.
        // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
        mCurrentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

        if (mIsInvert) {
            return BitmapManager.invert(bitmap);
        }
        return bitmap;
    }

    /**
     * Gets the number of pages in the PDF. This method is marked as public for testing.
     *
     * @return The number of pages.
     */
    public int getPageCount() {
        if (mPdfRenderer != null) {
            return mPdfRenderer.getPageCount();
        }
        return 0;
    }

    /**
     * Gets the number of the opened page.
     *
     * @return The number of the opened page.
     */
    public int getIndex() {
        if (mCurrentPage != null) {
            return mCurrentPage.getIndex();
        }
        return -1;
    }

    public void setScale(int scale) {
        mScale = scale;
    }

    public void setInvert(boolean invert) {
        mIsInvert = invert;
    }

    public boolean isInvert() {
        return mIsInvert;
    }

    public int getBackgroundColor() {
        if (mIsInvert) {
            return Color.rgb(0, 0, 0);
        }
        return Color.rgb(255, 255, 255);
    }

    public void updateUI(ImageButton previous, ImageButton next) {
        if (getPageCount() == 1) {
            previous.setEnabled(false);
            next.setEnabled(false);
        } else {
            previous.setEnabled(0 != getIndex());
            next.setEnabled(getIndex() + 1 < getPageCount());
        }
    }

    public File getFile() {
        return mFile;
    }
}
