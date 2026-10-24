package org.vosk.demo;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Log;

public class AsistenteAccessibilityService extends AccessibilityService {

    private static AsistenteAccessibilityService instance;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d("AsistenteService", "Servicio de accesibilidad conectado");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Por ahora no necesitamos procesar eventos
    }

    @Override
    public void onInterrupt() {
        Log.d("AsistenteService", "Servicio interrumpido");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    // Método para obtener la instancia desde otras clases
    public static AsistenteAccessibilityService getInstance() {
        return instance;
    }

    // ========== ACCIONES BÁSICAS ==========

    // Ir a la pantalla de inicio
    public void irAInicio() {
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    // Botón atrás
    public void irAtras() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    // Abrir recientes
    public void abrirRecientes() {
        performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    // Abrir notificaciones
    public void abrirNotificaciones() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

    // Bloquear pantalla
    public void bloquearPantalla() {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    // Tomar captura de pantalla
    public void capturaPantalla() {
        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
    }

    // Toca un elemento que contenga el texto indicado
    public boolean tocarTexto(String textoBuscado) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            Log.d("AsistenteService", "No se pudo obtener la ventana activa");
            return false;
        }

        textoBuscado = textoBuscado.toLowerCase().trim();
        boolean resultado = buscarYTocar(root, textoBuscado);
        root.recycle();
        return resultado;
    }

    // Función recursiva que busca el texto en todos los elementos
    private boolean buscarYTocar(AccessibilityNodeInfo node, String textoBuscado) {
        if (node == null) return false;

        // Revisar el texto del nodo actual
        CharSequence textoNodo = node.getText();
        CharSequence descripcion = node.getContentDescription();

        String texto = textoNodo != null ? textoNodo.toString().toLowerCase() : "";
        String desc = descripcion != null ? descripcion.toString().toLowerCase() : "";

        if ((texto.contains(textoBuscado) || desc.contains(textoBuscado)) && node.isClickable()) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.d("AsistenteService", "Se tocó: " + textoBuscado);
            return true;
        }

        // Buscar en los hijos
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo hijo = node.getChild(i);
            if (buscarYTocar(hijo, textoBuscado)) {
                if (hijo != null) hijo.recycle();
                return true;
            }
            if (hijo != null) hijo.recycle();
        }

        return false;
    }
}
