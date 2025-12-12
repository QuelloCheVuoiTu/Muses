# Tema Scuro MuSES Android

## Modifiche Implementate

### 1. **Nuova Palette Colori per Tema Scuro**
- Creato file `values-night/colors.xml` con palette ottimizzata
- Colori di sfondo scuri (#121212, #1E1E1E, #2D2D30)
- Testi in bianco/grigio chiaro per leggibilità
- Apple Blue più brillante (#409CFF) per mantenere l'identità del brand

### 2. **Nuovo Sistema Tema Completo**
- `values-night/themes.xml` completamente rinnovato
- Supporto Material Design 3 per tema scuro
- Status bar e navigation bar scure
- Colori appropriati per superficie, outline e stati

### 3. **ThemeManager Dinamico**
- Classe utility per gestire cambio tema
- Supporto per 3 modalità: Chiaro, Scuro, Segui sistema
- Persistenza delle preferenze utente
- Inizializzazione automatica all'avvio app

### 4. **Pagina Impostazioni**
- Nuova `SettingsActivity` con interfaccia moderna
- Selezione tema tramite dialog
- Design coerente con Material Design
- Aggiunta al drawer menu principale

### 5. **Pagina Login Ottimizzata**
- CardView con colori appropriati per entrambi i temi
- Testi e hint con contrasti corretti
- Sfondo dinamico che segue il tema

### 6. **Componenti Aggiornati**
- Stili per CardView, TextInputLayout, Button
- Color state list per input fields
- Ripple effects appropriati
- Dialog con tema coerente

## Come Usare

1. **Cambiare Tema**: Vai nel drawer menu → "Impostazioni" → "Tema"
2. **Modalità Disponibili**: 
   - Chiaro: Sempre tema chiaro
   - Scuro: Sempre tema scuro  
   - Segui sistema: Automatico secondo sistema Android

## File Modificati/Creati

### Nuovi File
- `values-night/colors.xml` - Palette colori tema scuro
- `values-night/themes.xml` - Tema scuro Material Design 3
- `values-night/styles.xml` - Stili componenti tema scuro
- `utils/ThemeManager.kt` - Manager per gestione temi
- `activities/SettingsActivity.kt` - Activity impostazioni
- `layout/activity_settings.xml` - Layout impostazioni
- `color/text_input_layout_stroke_color_dark.xml` - Color state list

### File Modificati
- `values/colors.xml` - Aggiunti colori mancanti per tema chiaro
- `values/themes.xml` - Aggiunto stile dialog
- `layout/activity_login.xml` - Colori dinamici per temi
- `menu/activity_main_drawer.xml` - Aggiunta voce impostazioni
- `MainActivity.kt` - Inizializzazione tema e navigazione impostazioni  
- `LoginActivity.kt` - Inizializzazione tema
- `AndroidManifest.xml` - Registrata SettingsActivity

## Palette Colori Tema Scuro

### Sfondi
- **Primario**: #121212 (Sfondo principale)
- **Secondario**: #1E1E1E (Card/Surface)  
- **Terziario**: #2D2D30 (Superficie elevata)

### Testi
- **Primario**: #FFFFFF (Bianco)
- **Secondario**: #B3B3B3 (Grigio chiaro)
- **Terziario**: #8A8A8A (Grigio medio)

### Brand
- **Apple Blue**: #409CFF (Più brillante per tema scuro)
- **Blue Dark**: #1E88E5
- **Blue Light**: #6BB6FF

Il tema mantiene l'identità visiva dell'app con il colore Apple Blue, ma ottimizzato per la leggibilità nel tema scuro.