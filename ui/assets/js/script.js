let currentMuseum = null;
let currentOpere = [];
let selectedOperaId = null;

// API Base URLs
const HOSTNAME = "http://it.unisannio.muses/muses"
const OPERE_API = HOSTNAME+'/artworks';
const MUSEUM_API = HOSTNAME+'/museums';
const REGISTER_API = HOSTNAME+'/register'
const LOGIN_API = HOSTNAME+'/login'

// Login function
async function login() {
    const id = document.getElementById('museumId').value.trim();
    const name = document.getElementById('museumName').value.trim();
    
    if (!id || !name) {
        showAlert('loginAlert', 'Inserisci ID e nome del museo', 'error');
        return;
    }
    
    try {
        // Verify museum exists
        const response = await fetch(`${MUSEUM_API}/getmuseums`);
        const data = await response.json();
        
        // Il server restituisce un oggetto con proprietà museums contenente l'array
        const museums = data.museums || data;
        
        const museum = museums.find(m => 
            m._id && m._id.toString() === id && m.name.toLowerCase() === name.toLowerCase()
        );
        
        if (museum) {
            currentMuseum = museum;
            showDashboard();
        } else {
            showAlert('loginAlert', 'Museo non trovato. Verifica ID e nome.', 'error');
        }
    } catch (error) {
        console.error('Errore login:', error);
        showAlert('loginAlert', 'Errore di connessione al server', 'error');
    }
}

// Register museum function
async function registerMuseum() {
    const name = document.getElementById('regName').value.trim();
    const description = document.getElementById('regDescrizione').value.trim();
    const latitude = document.getElementById('regLatitude').value;
    const longitude = document.getElementById('regLongitude').value;
    const hours = document.getElementById('regHours').value.trim();
    const price = document.getElementById('regPrice').value.trim();
    const rating = document.getElementById('regRating').value;
    const type = document.getElementById('regType').value.trim();
    const imageurl = document.getElementById('regImageurl').value.trim();
    const parent = document.getElementById('regParent').value.trim();
    
    if (!name || !description) {
        showAlert('loginAlert', 'Nome e descrizione sono obbligatori', 'error');
        return;
    }
    
    const museumData = {
        name: name,
        description: description,
        hours: hours,
        price: price,
        type: type,
        imageurl: imageurl,
        parent: parent
    };
    
    // Add location if provided
    if (latitude && longitude) {
        museumData.location = {
            latitude: parseFloat(latitude),
            longitude: parseFloat(longitude)
        };
    }
    
    // Add rating if provided
    if (rating) {
        museumData.rating = parseFloat(rating);
    }
    
    try {
        const response = await fetch(`${MUSEUM_API}/addmuseum`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(museumData)
        });
        
        if (response.ok) {
            showAlert('loginAlert', 'Museo registrato con successo!', 'success');
            clearRegisterForm();
        } else {
            showAlert('loginAlert', 'Errore nella registrazione del museo', 'error');
        }
    } catch (error) {
        showAlert('loginAlert', 'Errore di connessione al server', 'error');
    }
}
// Load museum info for editing
function loadMuseumForEdit() {
    if (!currentMuseum) return;
    
    document.getElementById('modMuseumName').value = currentMuseum.name || '';
    document.getElementById('modMuseumDescription').value = currentMuseum.description || '';
    document.getElementById('modMuseumType').value = currentMuseum.type || '';
    document.getElementById('modMuseumHours').value = currentMuseum.hours || '';
    document.getElementById('modMuseumPrice').value = currentMuseum.price || '';
    document.getElementById('modMuseumRating').value = currentMuseum.rating || '';
    document.getElementById('modMuseumParent').value = currentMuseum.parent || '';
    document.getElementById('modMuseumImageurl').value = currentMuseum.imageurl || '';
    
    // Handle location
    if (currentMuseum.location) {
        document.getElementById('modMuseumLatitude').value = currentMuseum.location.latitude || '';
        document.getElementById('modMuseumLongitude').value = currentMuseum.location.longitude || '';
    }
}
function debugApiCall(url, method, data) {
    console.log(`=== API CALL DEBUG ===`);
    console.log(`URL: ${url}`);
    console.log(`Method: ${method}`);
    console.log(`Data:`, data);
    console.log(`Current Museum ID: ${currentMuseum?._id}`);
    console.log(`=====================`);
}
// Modify museum form submission
document.getElementById('modifyMuseumForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    
    // Clear previous alerts
    document.getElementById('modifyMuseumAlert').innerHTML = '';
    
    if (!currentMuseum || !currentMuseum._id) {
        showAlert('modifyMuseumAlert', 'Errore: ID museo non trovato', 'error');
        return;
    }
    
    const name = document.getElementById('modMuseumName').value.trim();
    const description = document.getElementById('modMuseumDescription').value.trim();
    const latitude = document.getElementById('modMuseumLatitude').value;
    const longitude = document.getElementById('modMuseumLongitude').value;
    const hours = document.getElementById('modMuseumHours').value.trim();
    const price = document.getElementById('modMuseumPrice').value.trim();
    const rating = document.getElementById('modMuseumRating').value;
    const type = document.getElementById('modMuseumType').value.trim();
    const imageurl = document.getElementById('modMuseumImageurl').value.trim();
    const parent = document.getElementById('modMuseumParent').value.trim();
    
    if (!name || !description) {
        showAlert('modifyMuseumAlert', 'Nome e descrizione sono obbligatori', 'error');
        return;
    }
    
    const museumData = {
        name: name,
        description: description,
        hours: hours,
        price: price,
        type: type,
        imageurl: imageurl,
        parent: parent
    };
    
    // Add location if provided
    if (latitude && longitude) {
        museumData.location = {
            latitude: parseFloat(latitude),
            longitude: parseFloat(longitude)
        };
    }
    
    // Add rating if provided
    if (rating) {
        museumData.rating = parseFloat(rating);
    }
    
    // Show loading state
    showModifyingStatus();
    
    try {
        debugApiCall(`${MUSEUM_API}/modifymuseum/${currentMuseum._id}`, 'PUT', museumData);
        
        const response = await fetch(`${MUSEUM_API}/modifymuseum/${currentMuseum._id}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(museumData)
        });
        
        if (response.ok) {
            // Update currentMuseum object
            currentMuseum = { ...currentMuseum, ...museumData };
            
            // Update dashboard title
            document.getElementById('currentMuseumName').textContent = currentMuseum.name;
           
            // Update currentMuseum object
            currentMuseum = { ...currentMuseum, ...museumData };
            // Update dashboard title
            document.getElementById('currentMuseumName').textContent = currentMuseum.name;
            // Show persistent success message
            showAlert('modifyMuseumAlert', '✅ Museo modificato con successo! Le modifiche sono state salvate.', 'success');
            
            // Show persistent success message
            showAlert('modifyMuseumAlert', '✅ Museo modificato con successo! Le modifiche sono state salvate.', 'success');
            
            console.log('Museo aggiornato con successo');
            
        } else {
            let errorMessage = 'Errore nella modifica del museo';
            try {
                const errorData = await response.json();
                if (errorData.message) {
                    errorMessage += ': ' + errorData.message;
                }
            } catch (e) {
                errorMessage += ` (Status: ${response.status})`;
            }
            
            showAlert('modifyMuseumAlert', errorMessage, 'error');
            console.error('Errore risposta server:', response.status, errorMessage);
        }
    } catch (error) {
        console.error('Errore di connessione:', error);
        showAlert('modifyMuseumAlert', '❌ Errore di connessione al server. Riprova più tardi.', 'error');
    }
});
// Clear modify museum form
function clearModifyMuseumForm() {
    document.getElementById('modifyMuseumForm').reset();
    document.getElementById('modifyMuseumAlert').innerHTML = '';
    loadMuseumForEdit(); // Reload original data
}

// Show dashboard
function showDashboard() {
    document.getElementById('loginPage').style.display = 'none';
    document.getElementById('dashboard').style.display = 'block';
    document.getElementById('currentMuseumName').textContent = currentMuseum.name;
    
    // Aspetta un momento per assicurarsi che il DOM sia pronto
    setTimeout(() => {
        loadOpere();
        loadOpereForSelect();
        loadMuseumForEdit();
    }, 100);
}

// Logout function
function logout() {
    currentMuseum = null;
    currentOpere = [];
    selectedOperaId = null;
    
    document.getElementById('loginPage').style.display = 'block';
    document.getElementById('dashboard').style.display = 'none';
    
    // Clear forms
    clearLoginForm();
    clearRegisterForm();
    clearAddForm();
    clearModifyForm();
    
    // Reset to first tab WITHOUT calling showTab
    document.querySelectorAll('.tab-content').forEach(tab => {
        tab.classList.remove('active');
    });
    document.querySelectorAll('.tab').forEach(tab => {
        tab.classList.remove('active');
    });
    
    // Set first tab as active
    document.getElementById('opereTab').classList.add('active');
    document.querySelector('.tab').classList.add('active');
}

// Tab navigation
function showTab(tabName) {
    // Hide all tabs
    document.querySelectorAll('.tab-content').forEach(tab => {
        tab.classList.remove('active');
    });
    
    // Remove active class from all tab buttons
    document.querySelectorAll('.tab').forEach(tab => {
        tab.classList.remove('active');
    });
    
    // Show selected tab
    document.getElementById(tabName + 'Tab').classList.add('active');
    
    // Add active class to clicked tab button
    event.target.classList.add('active');
    
    // Load data for specific tabs
if (tabName === 'opere') {
    loadOpere();
} else if (tabName === 'modifica' || tabName === 'elimina') {
    loadOpereForSelect();
} else if (tabName === 'modificaMuseo') {
    loadMuseumForEdit();
}
}

// Load opere for current museum - versione corretta
async function loadOpere() {
    // Controllo di sicurezza
    if (!currentMuseum) {
        console.warn('currentMuseum è null, impossibile caricare le opere');
        return;
    }
    
    showLoading('opereLoading', true);
    
    try {
        const response = await fetch(`${OPERE_API}/getoperebymuseum/${encodeURIComponent(currentMuseum.name)}`);
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const result = await response.json();
        console.log('Risposta del server:', result); // Debug
        
        // Gestisce la struttura della risposta del server
        const opere = result.data || result;
        
        if (!Array.isArray(opere)) {
            throw new Error('La risposta del server non contiene un array di opere');
        }
        
        currentOpere = opere;
        displayOpere(opere);
        updateTypeDropdown(opere);
        
        console.log('Opere caricate:', opere.length); // Debug
    } catch (error) {
        console.error('Errore nel caricamento delle opere:', error);
        showAlert('opereAlert', 'Errore nel caricamento delle opere: ' + error.message, 'error');
    } finally {
        showLoading('opereLoading', false);
    }
}
// Aggiorna il dropdown dei tipi con i tipi effettivi delle opere del museo
function updateTypeDropdown(opere) {
    const searchType = document.getElementById('searchType');
    
    // Ottieni tutti i tipi unici dalle opere
    const types = [...new Set(opere.map(opera => opera.type))].sort();
    
    // Ricostruisci il dropdown
    searchType.innerHTML = '<option value="">Tutti i tipi</option>';
    
    types.forEach(type => {
        const option = document.createElement('option');
        option.value = type;
        option.textContent = type;
        searchType.appendChild(option);
    });
}

// Display opere in grid - versione migliorata
function displayOpere(opere) {
    const grid = document.getElementById('opereGrid');
    
    if (!grid) {
        console.error('Elemento opereGrid non trovato');
        return;
    }
    
    if (!opere || opere.length === 0) {
        grid.innerHTML = '<div class="loading">Nessuna opera trovata</div>';
        return;
    }
    
    try {
        const operaCards = opere.map(opera => {
            // Escaping delle stringhe per evitare problemi con caratteri speciali
            const safeName = (opera.name || 'Nome non disponibile').replace(/'/g, '&#39;').replace(/"/g, '&quot;');
            const safeType = (opera.type || 'Tipo non specificato').replace(/'/g, '&#39;').replace(/"/g, '&quot;');
            const safeMuseum = (opera.museum || 'Museo non specificato').replace(/'/g, '&#39;').replace(/"/g, '&quot;');
            const safeDescription = (opera.description || 'Nessuna descrizione').replace(/'/g, '&#39;').replace(/"/g, '&quot;');
            const safeImageUrl = opera.imageurl ? opera.imageurl.replace(/'/g, '&#39;').replace(/"/g, '&quot;') : '';
            const safeId = opera._id ? opera._id.replace(/'/g, '&#39;').replace(/"/g, '&quot;') : '';
            
            return `
                <div class="opera-card">
                    <h3>${safeName}</h3>
                    <p><strong>Tipo:</strong> ${safeType}</p>
                    <p><strong>Museo:</strong> ${safeMuseum}</p>
                    <p><strong>Descrizione:</strong> ${safeDescription}</p>
                    ${safeImageUrl ? `<img src="${safeImageUrl}" alt="${safeName}" style="width: 100%; height: 200px; object-fit: cover; border-radius: 10px; margin-top: 10px;" onerror="this.style.display='none'">` : ''}
                    <div class="opera-actions">
                        <button class="btn-edit" onclick="editOperaQuick('${safeId}')">Modifica</button>
                        <button class="btn-delete" onclick="deleteOperaQuick('${safeId}')">Elimina</button>
                    </div>
                </div>
            `;
        });
        
        grid.innerHTML = operaCards.join('');
    } catch (error) {
        console.error('Errore nella generazione delle carte opere:', error);
        grid.innerHTML = '<div class="loading">Errore nella visualizzazione delle opere</div>';
    }
}

// Search opere - versione corretta con controlli di sicurezza
async function searchOpere() {
    const searchTerm = document.getElementById('searchInput').value.trim();
    const searchType = document.getElementById('searchType').value;
    
    // Verifica che currentOpere esista
    if (!currentOpere || currentOpere.length === 0) {
        showAlert('opereAlert', 'Nessuna opera caricata. Ricarica la pagina.', 'error');
        return;
    }
    
    showLoading('opereLoading', true);
    
    try {
        let opere = [...currentOpere]; // Crea una copia dell'array
        
        // Filtra per nome se specificato
        if (searchTerm) {
            opere = opere.filter(opera => 
                opera.name && opera.name.toLowerCase().includes(searchTerm.toLowerCase())
            );
        }
        
        // Filtra per tipo se specificato
        if (searchType) {
            opere = opere.filter(opera => opera.type === searchType);
        }
        
        displayOpere(opere);
    } catch (error) {
        console.error('Errore nella ricerca:', error);
        showAlert('opereAlert', 'Errore nella ricerca', 'error');
    } finally {
        showLoading('opereLoading', false);
    }
}

// Add opera
document.getElementById('addOperaForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    
    const operaData = {
        name: document.getElementById('addNome').value.trim(),
        type: document.getElementById('addType').value.trim(),
        description: document.getElementById('addDescrizione').value.trim(),
        museum: currentMuseum.name,
        imageurl: document.getElementById('addImageurl').value.trim()
    };
    
    try {
        const response = await fetch(`${OPERE_API}/addopera`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(operaData)
        });
        
        if (response.ok) {
            showAlert('addAlert', 'Opera aggiunta con successo!', 'success');
            clearAddForm();
            loadOpere();
            loadOpereForSelect();
        } else {
            showAlert('addAlert', 'Errore nell\'aggiunta dell\'opera', 'error');
        }
    } catch (error) {
        showAlert('addAlert', 'Errore di connessione al server', 'error');
    }
});

// Load opere for select dropdowns
async function loadOpereForSelect() {
    try {
        const response = await fetch(`${OPERE_API}/getoperebymuseum/${encodeURIComponent(currentMuseum.name)}`);
        const result = await response.json();
        
        // Gestisce la struttura della risposta del server
        const opere = result.data || result;
        
        const operaSelect = document.getElementById('operaSelect');
        const deleteOperaSelect = document.getElementById('deleteOperaSelect');
        
        const options = opere.map(opera => 
            `<option value="${opera._id}">${opera.name} - ${opera.type}</option>`
        ).join('');
        
        operaSelect.innerHTML = '<option value="">Seleziona un\'opera</option>' + options;
        deleteOperaSelect.innerHTML = '<option value="">Seleziona un\'opera</option>' + options;
    } catch (error) {
        console.error('Errore nel caricamento delle opere per select:', error);
    }
}

// Load opera for editing
async function loadOperaForEdit() {
    const operaId = document.getElementById('operaSelect').value;
    
    if (!operaId) {
        document.getElementById('modifyOperaForm').classList.add('hidden');
        return;
    }
    
    try {
        const response = await fetch(`${OPERE_API}/getopera/${operaId}`);
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const result = await response.json();
        
        // Gestisce la struttura della risposta del server
        const opera = result.data || result;
        
        // Riempi i campi del form
        document.getElementById('modNome').value = opera.name || '';
        document.getElementById('modType').value = opera.type || '';
        document.getElementById('modDescrizione').value = opera.description || '';
        document.getElementById('modImageurl').value = opera.imageurl || '';
        
        selectedOperaId = operaId;
        document.getElementById('modifyOperaForm').classList.remove('hidden');
        
        // Pulisci eventuali alert precedenti
        document.getElementById('modifyAlert').innerHTML = '';
        
    } catch (error) {
        console.error('Errore nel caricamento dell\'opera:', error);
        showAlert('modifyAlert', 'Errore nel caricamento dell\'opera: ' + error.message, 'error');
        document.getElementById('modifyOperaForm').classList.add('hidden');
    }
}

// Modify opera
document.getElementById('modifyOperaForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    
    if (!selectedOperaId) {
        showAlert('modifyAlert', 'Seleziona un\'opera da modificare', 'error');
        return;
    }
    
    const operaData = {
        name: document.getElementById('modNome').value.trim(),
        type: document.getElementById('modType').value.trim(),
        description: document.getElementById('modDescrizione').value.trim(),
        museum: currentMuseum.name,
        imageurl: document.getElementById('modImageurl').value.trim()
    };
    
    try {
        const response = await fetch(`${OPERE_API}/modifyopera/${selectedOperaId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(operaData)
        });
        
        if (response.ok) {
            showAlert('modifyAlert', 'Opera modificata con successo!', 'success');
            clearModifyForm();
            loadOpere();
            loadOpereForSelect();
        } else {
            showAlert('modifyAlert', 'Errore nella modifica dell\'opera', 'error');
        }
    } catch (error) {
        showAlert('modifyAlert', 'Errore di connessione al server', 'error');
    }
});

// Load opera for deletion
document.getElementById('deleteOperaSelect').addEventListener('change', async function() {
    const operaId = this.value;
    
    if (!operaId) {
        document.getElementById('deleteOperaInfo').classList.add('hidden');
        document.getElementById('deleteConfirmBtn').disabled = true;
        return;
    }
    
    try {
        const response = await fetch(`${OPERE_API}/getopera/${operaId}`);
        const result = await response.json();
        
        // Gestisce la struttura della risposta del server
        const opera = result.data || result;
        
        document.getElementById('deleteOperaName').textContent = opera.name;
        document.getElementById('deleteOperaAutore').textContent = opera.type;
        document.getElementById('deleteOperaTipo').textContent = opera.museum;
        document.getElementById('deleteOperaAnno').textContent = opera.description || 'Nessuna descrizione';
        document.getElementById('deleteOperaDescrizione').textContent = opera.imageurl || 'Nessuna immagine';
        
        document.getElementById('deleteOperaInfo').classList.remove('hidden');
        document.getElementById('deleteConfirmBtn').disabled = false;
        selectedOperaId = operaId;
    } catch (error) {
        showAlert('deleteAlert', 'Errore nel caricamento dell\'opera', 'error');
    }
});

// Delete opera
async function deleteOpera() {
    if (!selectedOperaId) {
        showAlert('deleteAlert', 'Seleziona un\'opera da eliminare', 'error');
        return;
    }
    
    if (!confirm('Sei sicuro di voler eliminare questa opera? Questa azione non può essere annullata.')) {
        return;
    }
    
    try {
        const response = await fetch(`${OPERE_API}/deleteopera/${selectedOperaId}`, {
            method: 'DELETE'
        });
        
        if (response.ok) {
            showAlert('deleteAlert', 'Opera eliminata con successo!', 'success');
            
            // Reset delete form
            document.getElementById('deleteOperaSelect').value = '';
            document.getElementById('deleteOperaInfo').classList.add('hidden');
            document.getElementById('deleteConfirmBtn').disabled = true;
            selectedOperaId = null;
            
            // Reload data
            loadOpere();
            loadOpereForSelect();
        } else {
            showAlert('deleteAlert', 'Errore nell\'eliminazione dell\'opera', 'error');
        }
    } catch (error) {
        showAlert('deleteAlert', 'Errore di connessione al server', 'error');
    }
}

// Quick edit from opere grid
function editOperaQuick(operaId) {
    // Switch to modify tab
    showTab('modifica');
    document.querySelector('.tab:nth-child(3)').classList.add('active');
    
    // Set the select value and load the opera
    document.getElementById('operaSelect').value = operaId;
    loadOperaForEdit();
}

// Quick delete from opere grid
async function deleteOperaQuick(operaId) {
    if (!confirm('Sei sicuro di voler eliminare questa opera? Questa azione non può essere annullata.')) {
        return;
    }
    
    try {
        const response = await fetch(`${OPERE_API}/deleteopera/${operaId}`, {
            method: 'DELETE'
        });
        
        if (response.ok) {
            showAlert('opereAlert', 'Opera eliminata con successo!', 'success');
            loadOpere();
            loadOpereForSelect();
        } else {
            showAlert('opereAlert', 'Errore nell\'eliminazione dell\'opera', 'error');
        }
    } catch (error) {
        showAlert('opereAlert', 'Errore di connessione al server', 'error');
    }
}

// Utility functions
function showAlert(containerId, message, type) {
    const container = document.getElementById(containerId);
    if (!container) {
        console.warn(`Container ${containerId} non trovato`);
        return;
    }
    
    container.innerHTML = `<div class="alert alert-${type === 'error' ? 'error' : 'success'}">${message}</div>`;
    
    // Auto-hide success alerts dopo 5 secondi invece di 3
    if (type === 'success') {
        setTimeout(() => {
            if (container.innerHTML.includes(message)) {
                container.innerHTML = '';
            }
        }, 5000);
    }
}
function showModifyingStatus() {
    const submitBtn = document.querySelector('#modifyMuseumForm button[type="submit"]');
    const originalText = submitBtn.textContent;
    
    submitBtn.textContent = 'Modifica in corso...';
    submitBtn.disabled = true;
    
    // Ripristina dopo 3 secondi
    setTimeout(() => {
        submitBtn.textContent = originalText;
        submitBtn.disabled = false;
    }, 3000);
}

// Utility function corretta con controllo di esistenza
function showLoading(loadingId, show) {
    const loading = document.getElementById(loadingId);
    if (!loading) {
        console.warn(`Elemento con id '${loadingId}' non trovato`);
        return;
    }
    
    if (show) {
        loading.classList.remove('hidden');
    } else {
        loading.classList.add('hidden');
    }
}

function clearLoginForm() {
    document.getElementById('museumId').value = '';
    document.getElementById('museumName').value = '';
    document.getElementById('loginAlert').innerHTML = '';
}

function clearRegisterForm() {
    document.getElementById('regName').value = '';
    document.getElementById('regDescrizione').value = '';
    document.getElementById('regLatitude').value = '';
    document.getElementById('regLongitude').value = '';
    document.getElementById('regHours').value = '';
    document.getElementById('regPrice').value = '';
    document.getElementById('regRating').value = '';
    document.getElementById('regType').value = '';
    document.getElementById('regImageurl').value = '';
    document.getElementById('regParent').value = '';
}

function clearAddForm() {
    document.getElementById('addOperaForm').reset();
    document.getElementById('addAlert').innerHTML = '';
}

function clearModifyForm() {
    document.getElementById('modifyOperaForm').reset();
    document.getElementById('operaSelect').value = '';
    document.getElementById('modifyOperaForm').classList.add('hidden');
    document.getElementById('modifyAlert').innerHTML = '';
    selectedOperaId = null;
}

// Handle Enter key in search
document.getElementById('searchInput').addEventListener('keypress', function(e) {
    if (e.key === 'Enter') {
        searchOpere();
    }
});

// Initialize tabs on page load
document.addEventListener('DOMContentLoaded', function() {
    // Set up click handlers for tabs
    document.querySelectorAll('.tab').forEach((tab, index) => {
        tab.addEventListener('click', function() {
            const tabNames = ['opere', 'aggiungi', 'modifica', 'elimina', 'modificaMuseo'];
            showTab(tabNames[index]);
        });
    });
});