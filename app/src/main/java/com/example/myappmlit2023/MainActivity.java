package com.example.myappmlit2023;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;


import com.example.myappmlit2023.ml.FlowerModel;
import com.example.myappmlit2023.ml.ModeloFlores;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;


import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;

import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;

public class MainActivity
        extends AppCompatActivity
    implements OnSuccessListener<Text>,
        OnFailureListener {

    public static int REQUEST_CAMERA = 111;
    public static int REQUEST_GALLERY = 222;

    Bitmap mSelectedImage;
    ImageView mImageView;
    TextView txtResults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImageView = findViewById(R.id.image_view);
        txtResults = findViewById(R.id.txtresults);
    }

    public void abrirGaleria (View view){
        Intent i = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(i, REQUEST_GALLERY);
    }
    public void abrirCamera (View view){
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, REQUEST_CAMERA);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && null != data) {
            try {
                if (requestCode == REQUEST_CAMERA)
                    mSelectedImage = (Bitmap) data.getExtras().get("data");
                else
                    mSelectedImage = MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData());
                mImageView.setImageBitmap(mSelectedImage);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void OCRfx(View v) {
        InputImage image = InputImage.fromBitmap(mSelectedImage, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer.process(image)
                .addOnSuccessListener(this)
                .addOnFailureListener(this);
    }

    @Override
    public void onFailure(@NonNull Exception e) {

    }

    @Override
    public void onSuccess(Text text) {
        List<Text.TextBlock> blocks = text.getTextBlocks();
        String resultados="";
        if (blocks.size() == 0) {
            resultados = "No hay Texto";
        }else{
            for (int i = 0; i < blocks.size(); i++) {
                List<Text.Line> lines = blocks.get(i).getLines();
                for (int j = 0; j < lines.size(); j++) {
                    List<Text.Element> elements = lines.get(j).getElements();
                    for (int k = 0; k < elements.size(); k++) {
                        resultados = resultados + elements.get(k).getText() + " ";
                    }
                }
                resultados=resultados + "\n";
            }
            resultados=resultados + "\n";
        }
        txtResults.setText(resultados);
    }
    public void Rostrosfx(View v) {
        BitmapDrawable drawable = (BitmapDrawable) mImageView.getDrawable();
        Bitmap bitmap = drawable.getBitmap().copy(Bitmap.Config.ARGB_8888,true);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(Color.BLUE);
        paint.setStrokeWidth(15);
        paint.setStyle(Paint.Style.STROKE);

        InputImage image = InputImage.fromBitmap(mSelectedImage, 0);
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                        .build();
        FaceDetector detector = FaceDetection.getClient(options);
        detector.process(image)
                .addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                    @Override
                    public void onSuccess(List<Face> faces) {
                        if (faces.size() == 0) {
                            txtResults.setText("No Hay rostros");
                        }else{
                            txtResults.setText("Hay " + faces.size() + " rostro(s)");

                            for (Face rostro: faces) {
                               canvas.drawRect(rostro.getBoundingBox(), paint);
                            }

                        }
                    }
                })
                .addOnFailureListener(this);

        mImageView.setImageBitmap(bitmap);
    }




    public void PersonalizedModel1(View v) {
        try {
            FlowerModel model = FlowerModel.newInstance(getApplicationContext());
            TensorImage imagen = TensorImage.fromBitmap(mSelectedImage);
            FlowerModel.Outputs outputs = model.process(imagen);
            List<Category> probability = outputs.getProbabilityAsCategoryList();
            Collections.sort(probability, new CategoryComparator());
            String res="";
            for (int i = 0; i < probability.size(); i++) {
                res = res + probability.get(i).getLabel() + " " + probability.get(i).getScore()*100 + " % \n";
            }
            txtResults.setText(res);
            model.close();
        } catch (IOException e) {
            txtResults.setText("Error al procesar Modelo");
        }
    }

    public ByteBuffer convertirImagenATensorBuffer(Bitmap mSelectedImage){

        Bitmap imagen = Bitmap.createScaledBitmap(mSelectedImage, 224, 224, true);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[224 * 224];
        imagen.getPixels(intValues, 0, imagen.getWidth(), 0, 0, imagen.getWidth(), imagen.getHeight());

        int pixel = 0;

        for(int i = 0; i <  imagen.getHeight(); i ++){
            for(int j = 0; j < imagen.getWidth(); j++){
                int val = intValues[pixel++]; // RGB
                byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255.f));
                byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255.f));
                byteBuffer.putFloat((val & 0xFF) * (1.f / 255.f));
            }
        }
        return  byteBuffer;
    }

    public String obtenerEtiquetayProbabilidad(String[] etiquetas, float[] probabilidades){

        float valorMayor=Float.MIN_VALUE;
        int pos=-1;
        for (int i = 0; i < probabilidades.length; i++) {
            if (probabilidades[i] > valorMayor) {
                valorMayor = probabilidades[i];
                pos = i;
            }
        }

        return "Predicción: " + etiquetas[pos] + ", Probabilidad: " + (new DecimalFormat("0.00").format(probabilidades[pos] * 100)) + "%";

    }

    public void PersonalizedModel(View v){
        try {

            //Definir Estiquetas de acuerdo a su archivo "labels.txt" generado por la Plataforma de creación del Modelo
            String[] etiquetas = {"Dientes de León", "Margaritas","Tulipanes", "Girasoles", "Rosas"};

            ModeloFlores model = ModeloFlores.newInstance(getApplicationContext());
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
            inputFeature0.loadBuffer(convertirImagenATensorBuffer(mSelectedImage));

            ModeloFlores.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

            txtResults.setText(obtenerEtiquetayProbabilidad(etiquetas, outputFeature0.getFloatArray()));

            model.close();
        } catch (Exception e) {
            txtResults.setText(e.getMessage());
        }
    }

    public void PersonalizedModel33(View v) {
        try {
            ModeloFlores model = ModeloFlores.newInstance(getApplicationContext());

            Bitmap imagen_escalada = Bitmap.createScaledBitmap(mSelectedImage,
                    224,224,true);


            ImageProcessor imageProcessor =
                    new ImageProcessor.Builder()
                            .add(new ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
                            .add(new Rot90Op(-90 / 90))
                            .build();

            TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
            tensorImage.load(mSelectedImage);
            tensorImage = imageProcessor.process(tensorImage);


            //TensorImage imagen = new TensorImage(DataType.FLOAT32);
            //imagen.load(imagen_escalada);
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
            inputFeature0.loadBuffer(tensorImage.getBuffer());

            ModeloFlores.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

            String[] etiquetas = {"Dientes de León", "Margaritas","Tulipanes", "Girasoles", "Rosas"};

            float[] probabilidades = outputFeature0.getFloatArray();
            float valorMayor=Float.MIN_VALUE;     int pos=-1;
            String  res="";
            for (int i = 0; i < probabilidades.length; i++) {
                if(probabilidades[i]>valorMayor){
                    valorMayor = probabilidades[i];
                    pos = i;
                }

                res = res + etiquetas[i] + " " + probabilidades[i]*100 + "\n";
            }
            txtResults.setText(res);

            model.close();
        } catch (IOException e) {
            // TODO Handle the exception
        }

    }


    class CategoryComparator implements java.util.Comparator<Category> {
        @Override
        public int compare(Category a, Category b) {
            return (int)(b.getScore()*100) - (int)(a.getScore()*100);
        }
    }
}