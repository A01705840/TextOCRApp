package com.example.kotlin.textocrapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.Manifest
import android.os.Bundle
import android.os.Environment
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var cameraImage: ImageView
    private lateinit var captureImgBtn: Button
    private lateinit var resultText : TextView
    private var currentPhotoPath: String? = null
    private var photoUri: Uri? = null
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var cropImageLauncher: ActivityResultLauncher<CropImageContractOptions>



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraImage = findViewById(R.id.cameraImage)
        captureImgBtn = findViewById(R.id.captureImgButton)
        resultText = findViewById(R.id.resultText)

        savedInstanceState?.let {
            val savedUri = it.getParcelable<Uri>("photoUri")
            if (savedUri != null) {
                photoUri = savedUri
            }
        }

        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){
            isGranted ->
            if (isGranted){
                captureImage()
            } else {
                Toast.makeText(this, "Camera Permision Denied", Toast.LENGTH_SHORT).show()
            }
        }

        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()){
            success ->
            if (success && photoUri != null){
                /*currentPhotoPath?.let { path ->
                    val bitmap = BitmapFactory.decodeFile(path)
                    cameraImage.setImageBitmap(bitmap)
                    recognizeText(bitmap)

                }*/
                launchImageCropper(photoUri!!)
            }
        }

        cropImageLauncher = registerForActivityResult(CropImageContract()){
            result ->
            if (result.isSuccessful){
                val croppedUri = result.uriContent
                try{
                    val inputStream = contentResolver.openInputStream(croppedUri!!)
                    val bitmap = BitmapFactory.decodeStream(inputStream)

                    cameraImage.setImageBitmap(bitmap)

                    recognizeText(bitmap)
                }catch (e: Exception){
                    Log.e("Main Activity", "Error loading cropped image", e)
                }
            }else {
                val error = result.error
                Log.e("Main Activity", "Crop Error ${error?.message}")
            }
        }

        captureImgBtn.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

    }

    // Capturar Foto Temporal de Galeria

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(
            Date()
        )
        //Accesar a imagenes de telefono
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    // Capturar Foto Temporal con Prevencion de Error

    private fun captureImage(){
        val photoFile: File? = try{
            createImageFile()
        } catch (ex: IOException){
            Toast.makeText(this, "Ocurrió un error mientras se creaba el archivo", Toast.LENGTH_SHORT).show()
            null
        }
        if (photoFile != null) {
            try {
                // Create URI and store it as a class property
                photoUri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    photoFile
                )

                Log.d("MainActivity", "Created photo URI: $photoUri")

                // Launch camera with the URI
                takePictureLauncher.launch(photoUri!!)
            } catch (ex: Exception) {
                Log.e("MainActivity", "Error creating URI or launching camera", ex)
                Toast.makeText(this, "Error: ${ex.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No se pudo crear el archivo para la foto", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save the photo URI
        photoUri?.let { outState.putParcelable("photoUri", it) }
    }

    private fun launchImageCropper(uri: Uri){
        val cropOptions = CropImageOptions().apply {
            guidelines = CropImageView.Guidelines.ON
            fixAspectRatio = false
            cropShape = CropImageView.CropShape.RECTANGLE
            showProgressBar = true

            activityTitle = "Crop Image"

            // Make sure crop controls are visible
            showCropOverlay = true

            // Enable image flipping and rotation controls
            allowFlipping = true
            allowRotation = true

            // Set crop menu crop button title
            cropMenuCropButtonTitle = "Done"


            // Set activity menu text colors
            activityMenuTextColor = android.graphics.Color.WHITE

            // Ensure back button is shown
            skipEditing = false

            // Set other UI options
            toolbarColor = android.graphics.Color.BLACK
            toolbarBackButtonColor = android.graphics.Color.WHITE
            toolbarTintColor = android.graphics.Color.WHITE

        }

        val cropImageContractOptions = CropImageContractOptions(uri, cropOptions)
        cropImageLauncher.launch(cropImageContractOptions)
    }

    private fun recognizeText(bitmap: Bitmap){
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image).addOnSuccessListener { ocrText ->
            val numbersOnly = ocrText.text.filter { it.isDigit() }
            resultText.text = numbersOnly
            resultText.movementMethod = ScrollingMovementMethod()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "No se pudo reconocer el texto: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
