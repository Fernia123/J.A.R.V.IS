package org.vosk.demo;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        }
    }

    // Tomar captura de pantalla
    public void capturaPantalla() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
        }
    }

    // ==================== CONTROL DE PANTALLA ====================

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

    private boolean buscarYTocar(AccessibilityNodeInfo node, String textoBuscado) {
        if (node == null) return false;

        CharSequence textoNodo = node.getText();
        CharSequence descripcion = node.getContentDescription();

        String texto = textoNodo != null ? textoNodo.toString().toLowerCase() : "";
        String desc = descripcion != null ? descripcion.toString().toLowerCase() : "";

        if (texto.contains(textoBuscado) || desc.contains(textoBuscado)) {
            if (node.isClickable()) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d("AsistenteService", "Se tocó: " + textoBuscado);
                return true;
            }
            // Si el nodo no es clickable, intentamos hacer clic en el padre
            AccessibilityNodeInfo padre = node.getParent();
            if (padre != null && padre.isClickable()) {
                padre.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                padre.recycle();
                Log.d("AsistenteService", "Se tocó padre de: " + textoBuscado);
                return true;
            }
            if (padre != null) padre.recycle();
        }

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

    // Desplazarse hacia abajo
    public void desplazarAbajo() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            root.recycle();
        }
    }

    // Desplazarse hacia arriba
    public void desplazarArriba() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            root.recycle();
        }
    }

    // Escribir texto en el campo que esté enfocado
    public boolean escribirTexto(String texto) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) {
            // Buscar el primer campo editable
            focused = buscarCampoEditable(root);
        }

        if (focused != null && focused.isEditable()) {
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, texto);
            boolean exito = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            focused.recycle();
            root.recycle();
            return exito;
        }

        if (focused != null) focused.recycle();
        root.recycle();
        return false;
    }

    private AccessibilityNodeInfo buscarCampoEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable()) return node;

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo hijo = node.getChild(i);
            AccessibilityNodeInfo resultado = buscarCampoEditable(hijo);
            if (resultado != null) {
                if (hijo != null && hijo != resultado) hijo.recycle();
                return resultado;
            }
            if (hijo != null) hijo.recycle();
        }
        return null;
    }

    // Borrar el texto del campo enfocado
    public void borrarTexto() {
        escribirTexto("");
    }
}
