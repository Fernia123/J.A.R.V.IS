// Copyright 2019 Alpha Cephei Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.vosk.demo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class VoskActivity extends Activity implements
        RecognitionListener {

    static private final int STATE_START = 0;
    static private final int STATE_READY = 1;
    static private final int STATE_DONE = 2;
    static private final int STATE_FILE = 3;
    static private final int STATE_MIC = 4;

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private Model model;
    private SpeechService speechService;
    private SpeechStreamService speechStreamService;
    private TextView resultView;

    private TextToSpeech tts;
    private boolean ttsListo = false;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);

        // Setup layout
        resultView = findViewById(R.id.result_text);
        setUiState(STATE_START);

        findViewById(R.id.recognize_file).setOnClickListener(view -> recognizeFile());
        findViewById(R.id.recognize_mic).setOnClickListener(view -> recognizeMicrophone());
        ((ToggleButton) findViewById(R.id.pause)).setOnCheckedChangeListener((view, isChecked) -> pause(isChecked));

        LibVosk.setLogLevel(LogLevel.INFO);

        // Check if user has given permission to record audio, init the model after permission is granted
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            initModel();
        }
        verificarServicioAccesibilidad();
        inicializarVoz();
    }

    private void initModel() {
        StorageService.unpack(this, "model-es", "model",
                (model) -> {
                    this.model = model;
                    setUiState(STATE_READY);
                },
                (exception) -> setErrorState("Failed to unpack the model: " + exception.getMessage()));
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is time-consuming and involves IO,
                // so we execute it in async task
                initModel();
            } else {
                finish();
            }
        }
    }

    private void inicializarVoz() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int resultado = tts.setLanguage(new Locale("es", "ES"));
                if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Idioma español no soportado");
                } else {
                    ttsListo = true;
                    tts.setSpeechRate(1.0f); // velocidad normal
                    tts.setPitch(1.0f);     // tono normal
                }
            }
        });
    }
    private void hablar(String mensaje) {
        if (ttsListo && tts != null) {
            tts.speak(mensaje, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
            speechService = null;
        }

        if (speechStreamService != null) {
            speechStreamService.stop();
            speechStreamService = null;
        }

        if (model != null) {
            model.close();
            model = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    @Override
    public void onResult(String hypothesis) {
        // Esto ya debería estar (muestra el texto)
        ((TextView) findViewById(R.id.result_text)).setText(hypothesis);

        // --- NUEVO: Interpretar comandos ---
        String texto = hypothesis.toLowerCase()
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ü", "u")
                .replaceAll("[^a-z\\s]", "")
                .trim();

        AsistenteAccessibilityService servicio = AsistenteAccessibilityService.getInstance();

        if (texto.contains("whatsapp")) {
            abrirApp("com.whatsapp");
        }
        else if (texto.contains("youtube") || texto.contains("you tube")) {
            if (servicio != null) {
                abrirApp("com.google.android.youtube");
                hablar("Qué vera el día de hoy");
            }
        }
        else if (texto.contains("chrome") || texto.contains("crom") || texto.contains("navegador")) {
            if (servicio != null ) {
                abrirApp("com.android.chrome");
                hablar("¿Qué buscara el día de hoy señor?");
            }

        }
        else if (texto.contains("instagram") || texto.contains("insta")) {
            abrirApp("com.instagram.android");
        }
        else if (texto.contains("configuracion") || texto.contains("ajustes") || texto.contains("settings")) {
            startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
        }
// ===== Comandos con Accessibility Service =====
        else if (texto.contains("inicio") || texto.contains("home") || texto.contains("pantalla de inicio")) {
            if (servicio != null) {
                servicio.irAInicio();
            } else {
                irAInicio(); // respaldo
            }
        }
        else if (texto.contains("atras") || texto.contains("volver") || texto.contains("regresar")) {
            if (servicio != null) {
                servicio.irAtras();
            }
        }
        else if (texto.contains("recientes") || texto.contains("aplicaciones recientes") || texto.contains("multitarea")) {
            if (servicio != null) {
                servicio.abrirRecientes();
            }
        }
        else if (texto.contains("notificaciones") || texto.contains("panel de notificaciones")) {
            if (servicio != null) {
                servicio.abrirNotificaciones();
            }
        }
        else if (texto.contains("bloquear") || texto.contains("bloquea la pantalla") || texto.contains("apaga la pantalla")) {
            if (servicio != null) {
                servicio.bloquearPantalla();
            }
        }
        else if (texto.contains("captura") || texto.contains("screenshot") || texto.contains("captura de pantalla")) {
            if (servicio != null) {
                servicio.capturaPantalla();
            }
        }
        else if (texto.contains("sube") && texto.contains("volumen")) {
            ajustarVolumen(true);
        }
        else if (texto.contains("baja") && texto.contains("volumen")) {
            ajustarVolumen(false);
        }
        else if (texto.startsWith("toca ") || texto.startsWith("presiona ") || texto.startsWith("haz clic en ") || texto.startsWith("click en ")) {
            if (servicio != null) {
                String textoATocar = texto
                        .replace("toca ", "")
                        .replace("presiona ", "")
                        .replace("haz clic en ", "")
                        .replace("click en ", "")
                        .trim();

                boolean exito = servicio.tocarTexto(textoATocar);
                if (!exito) {
                    Toast.makeText(this, "No encontré el botón: " + textoATocar, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onFinalResult(String hypothesis) {
        resultView.append(hypothesis + "\n");
        setUiState(STATE_DONE);
        if (speechStreamService != null) {
            speechStreamService = null;
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {
        resultView.append(hypothesis + "\n");
    }

    @Override
    public void onError(Exception e) {
        setErrorState(e.getMessage());
    }

    @Override
    public void onTimeout() {
        setUiState(STATE_DONE);
    }

    private void setUiState(int state) {
        switch (state) {
            case STATE_START:
                resultView.setText(R.string.preparing);
                resultView.setMovementMethod(new ScrollingMovementMethod());
                findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_READY:
                resultView.setText(R.string.ready);
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_DONE:
                ((Button) findViewById(R.id.recognize_file)).setText(R.string.recognize_file);
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                ((ToggleButton) findViewById(R.id.pause)).setChecked(false);
                break;
            case STATE_FILE:
                ((Button) findViewById(R.id.recognize_file)).setText(R.string.stop_file);
                resultView.setText(getString(R.string.starting));
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_MIC:
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.stop_microphone);
                resultView.setText(getString(R.string.say_something));
                findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((true));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + state);
        }
    }

    private void setErrorState(String message) {
        resultView.setText(message);
        ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
        findViewById(R.id.recognize_file).setEnabled(false);
        findViewById(R.id.recognize_mic).setEnabled(false);
    }

    private void recognizeFile() {
        if (speechStreamService != null) {
            setUiState(STATE_DONE);
            speechStreamService.stop();
            speechStreamService = null;
        } else {
            setUiState(STATE_FILE);
            try {
                Recognizer rec = new Recognizer(model, 16000.0f);

                InputStream ais = getAssets().open(
                        "10001-90210-01803.wav");
                if (ais.skip(44) != 44) throw new IOException("File too short");

                speechStreamService = new SpeechStreamService(rec, ais, 16000);
                speechStreamService.start(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

    private void recognizeMicrophone() {
        if (speechService != null) {
            setUiState(STATE_DONE);
            speechService.stop();
            speechService = null;
        } else {
            setUiState(STATE_MIC);
            try {
                Recognizer rec = new Recognizer(model, 16000.0f);
                speechService = new SpeechService(rec, 16000.0f);
                speechService.startListening(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }


    private void pause(boolean checked) {
        if (speechService != null) {
            speechService.setPause(checked);
        }
    }

    private void abrirApp(String packageName) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else {
                // Intento alternativo
                Intent intent2 = new Intent(Intent.ACTION_MAIN);
                intent2.addCategory(Intent.CATEGORY_LAUNCHER);
                intent2.setPackage(packageName);
                intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                if (intent2.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent2);
                } else {
                    Toast.makeText(this, "No se encontró la aplicación: " + packageName, Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error al abrir: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void ajustarVolumen(boolean subir) {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (subir) {
            audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
        } else {
            audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
        }
    }

    private void irAInicio() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void verificarServicioAccesibilidad() {
        if (AsistenteAccessibilityService.getInstance() == null) {
            // El servicio no está activado
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Permiso necesario")
                    .setMessage("Para que el asistente pueda controlar el celular, debes activar el servicio de accesibilidad.")
                    .setPositiveButton("Activar", (dialog, which) -> {
                        Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        }
    }
}
