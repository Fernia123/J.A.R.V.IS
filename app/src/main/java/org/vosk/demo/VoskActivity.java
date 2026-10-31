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
    private boolean modoComando = false;
    private static final String PALABRA_CLAVE = "jarvis";
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable desactivarModoComando;

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

        if (desactivarModoComando != null) {
            handler.removeCallbacks(desactivarModoComando);
        }

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
        ((TextView) findViewById(R.id.result_text)).setText(hypothesis);

        String texto = hypothesis.toLowerCase()
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ü", "u")
                .replaceAll("[^a-z\\s]", "")
                .trim();

        if (texto.isEmpty()) {
            return;
        }

        if (!modoComando) {
            if (texto.contains(PALABRA_CLAVE)) {
                activarModoComando();
            }
            return;
        }

        procesarComando(texto);
    }

    private void procesarComando(String texto) {
        AsistenteAccessibilityService servicio = AsistenteAccessibilityService.getInstance();

        // 1. Abrir Aplicaciones
        if (texto.contains("whatsapp")) {
            abrirApp("com.whatsapp");
            hablar("Abriendo WhatsApp");
        }
        else if (texto.contains("youtube") || texto.contains("you tube")) {
            abrirApp("com.google.android.youtube");
            hablar("Abriendo YouTube, ¿qué verá hoy?");
        }
        else if (texto.contains("chrome") || texto.contains("crom") || texto.contains("navegador")) {
            abrirApp("com.android.chrome");
            hablar("Abriendo el navegador");
        }
        else if (texto.contains("instagram") || texto.contains("insta")) {
            abrirApp("com.instagram.android");
            hablar("Abriendo Instagram");
        }
        else if (texto.contains("configuracion") || texto.contains("ajustes") || texto.contains("settings")) {
            startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            hablar("Abriendo configuración");
        }

        // 2. Acciones del Sistema (Accessibility Service)
        else if (texto.contains("inicio") || texto.contains("home") || texto.contains("pantalla de inicio")) {
            if (servicio != null) {
                servicio.irAInicio();
            } else {
                irAInicio(); // respaldo
            }
            hablar("Yendo a la pantalla de inicio");
        }
        else if (texto.contains("atras") || texto.contains("volver") || texto.contains("regresar")) {
            if (servicio != null) {
                servicio.irAtras();
            }
            hablar("Volviendo atrás");
        }
        else if (texto.contains("recientes") || texto.contains("aplicaciones recientes") || texto.contains("multitarea")) {
            if (servicio != null) {
                servicio.abrirRecientes();
            }
            hablar("Abriendo aplicaciones recientes");
        }
        else if (texto.contains("notificaciones") || texto.contains("panel de notificaciones")) {
            if (servicio != null) {
                servicio.abrirNotificaciones();
            }
            hablar("Abriendo panel de notificaciones");
        }
        else if (texto.contains("bloquear") || texto.contains("bloquea la pantalla") || texto.contains("apaga la pantalla")) {
            if (servicio != null) {
                servicio.bloquearPantalla();
            }
            hablar("Bloqueando la pantalla");
        }
        else if (texto.contains("captura") || texto.contains("screenshot") || texto.contains("captura de pantalla")) {
            if (servicio != null) {
                servicio.capturaPantalla();
            }
            hablar("Tomando captura de pantalla");
        }

        // 3. Ajustes de Volumen
        else if ((texto.contains("sube") || texto.contains("subir")) && texto.contains("volumen")) {
            ajustarVolumen(true);
            hablar("Subiendo el volumen");
        }
        else if ((texto.contains("baja") || texto.contains("bajar")) && texto.contains("volumen")) {
            ajustarVolumen(false);
            hablar("Bajando el volumen");
        }

        // 4. Desplazamiento en Pantalla (Scroll)
        else if (texto.contains("desplazate hacia abajo") || texto.contains("desliza hacia abajo") || texto.contains("scroll down") || texto.equals("baja") || texto.contains("baja pantalla")) {
            if (servicio != null) {
                servicio.desplazarAbajo();
            }
            hablar("Desplazando hacia abajo");
        }
        else if (texto.contains("desplazate hacia arriba") || texto.contains("desliza hacia arriba") || texto.contains("scroll up") || texto.equals("sube") || texto.contains("sube pantalla")) {
            if (servicio != null) {
                servicio.desplazarArriba();
            }
            hablar("Desplazando hacia arriba");
        }

        // 5. Escritura y Borrado de Texto
        else if (texto.startsWith("escribe ") || texto.startsWith("escribir ")) {
            String mensaje = texto.replace("escribe ", "").replace("escribir ", "").trim();
            if (servicio != null) {
                boolean exito = servicio.escribirTexto(mensaje);
                hablar(exito ? "Escribiendo " + mensaje : "No encontré un campo de texto");
            }
        }
        else if (texto.contains("borra el texto") || texto.contains("borrar texto") || texto.contains("limpia el texto")) {
            if (servicio != null) {
                servicio.borrarTexto();
            }
            hablar("Texto borrado");
        }

        // 6. Tocados e interacción táctil por texto
        else if (texto.startsWith("toca ") || texto.startsWith("presiona ") || texto.startsWith("haz clic en ") || texto.startsWith("click en ")) {
            String objetivo = texto
                    .replace("toca ", "")
                    .replace("presiona ", "")
                    .replace("haz clic en ", "")
                    .replace("click en ", "")
                    .trim();

            if (servicio != null) {
                boolean exito = servicio.tocarTexto(objetivo);
                if (exito) {
                    hablar("Tocando " + objetivo);
                } else {
                    hablar("No encontré " + objetivo);
                    Toast.makeText(this, "No encontré el botón: " + objetivo, Toast.LENGTH_SHORT).show();
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

    private void activarModoComando() {
        modoComando = true;
        hablar("Dime");

        // Cancelar temporizador anterior si existe
        if (desactivarModoComando != null) {
            handler.removeCallbacks(desactivarModoComando);
        }

        // Después de 8 segundos vuelve a modo espera
        desactivarModoComando = () -> {
            modoComando = false;
            // Opcional: hablar("Modo espera");
        };
        handler.postDelayed(desactivarModoComando, 8000); // 8 segundos
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
