var currentMuseum = undefined
loadMuseum()

function loadMuseum(){
    var museum_id=localStorage.getItem("museum_id")
    authorizedFetch(`${MUSEUM_API}/${museum_id}`).then(async resp=>{
         if (! resp.ok){
            console.error("Could not fetch museum")
            location.href = "/ui/"
        }
        currentMuseum = await resp.json();
        document.getElementById('currentMuseumName').textContent = currentMuseum.name;
        loadOpere();
        loadRewards();
        loadOpereForSelect();
        loadMuseumForEdit();
    }).catch(_=>{
            console.error("Something went wrong in museum data parsing")
            console.error(_)
        })
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

// Load opera for editing
async function loadOperaForEdit() {
    const operaId = document.getElementById('operaSelect').value;
    
    if (!operaId) {
        document.getElementById('modifyOperaForm').classList.add('hidden');
        return;
    }
    
    try {
        const response = await authorizedFetch(`${OPERE_API}/${operaId}`);
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const result = await response.json();
        
        // Gestisce la struttura della risposta del server
        const opera = result.data || result;
        
        // Riempi i campi del form
        document.getElementById('modNome').value = opera.name || '';
        document.getElementById('modType').value = opera.types.join(", ") || '';
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
        const response = await authorizedFetch(`${OPERE_API}/modifyopera/${selectedOperaId}`, {
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
        const response = await authorizedFetch(`${OPERE_API}/${operaId}`);
        const result = await response.json();
        
        // Gestisce la struttura della risposta del server
        const opera = result.data || result;
        
        document.getElementById('deleteOperaName').textContent = opera.name;
        document.getElementById('deleteOperaAutore').textContent = opera.types.join(", ");
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
        const response = await authorizedFetch(`${OPERE_API}/${selectedOperaId}`, {
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
        const response = await authorizedFetch(`${OPERE_API}/${operaId}`, {
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
}else if (tabName === 'reward') {
    loadRewards();
}
else if (tabName === 'modifica' || tabName === 'elimina') {
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
        const response = await authorizedFetch(`${OPERE_API}/search?museum=${encodeURIComponent(localStorage.getItem("museum_id"))}`);
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const result = await response.json();
        console.log('Risposta del server:', result); // Debug
        
        if (result == null){
            showAlert('opereAlert', 'Nessuna opera collegata al museo', 'error');
            console.log("No artwork linked to this museum")
            return
        }
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
        showAlert('rewardAlert', 'Errore nel caricamento delle opere: ' + error.message, 'error');
    } finally {
        showLoading('opereLoading', false);
    }
}

// Load opere for current museum - versione corretta
async function loadRewards() {
    // Controllo di sicurezza
    if (!currentMuseum) {
        console.warn('currentMuseum è null, impossibile caricare le opere');
        return;
    }
    
    showLoading('rewardLoading', true);
    
    try {
        const response = await authorizedFetch(`${REWARD_API}/museum/${encodeURIComponent(localStorage.getItem("museum_id"))}`);
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const result = await response.json();
        console.log('Risposta del server:', result); // Debug
        
        if (result == null){
            showAlert('opereAlert', 'Nessuna opera collegata al museo', 'error');
            console.log("No artwork linked to this museum")
            return
        }
        // Gestisce la struttura della risposta del server
        const rewards = result.rewards || result;
        
        if (!Array.isArray(rewards)) {
            throw new Error('La risposta del server non contiene un array di rewards');
        }
        
        currentrewards = rewards;
        displayRewards(rewards);
        
        console.log('rewards caricate:', rewards.length); // Debug
    } catch (error) {
        console.error('Errore nel caricamento delle rewards:', error);
        showAlert('rewardsAlert', 'Errore nel caricamento delle rewards: ' + error.message, 'error');
    } finally {
        showLoading('rewardLoading', false);
    }
}

// Aggiorna il dropdown dei tipi con i tipi effettivi delle opere del museo
function updateTypeDropdown(opere) {
    const searchType = document.getElementById('searchType');
    
    // Ottieni tutti i tipi unici dalle opere
    const types = [...new Set(opere.map(opera => opera.types.map(t => t)))].sort();
    
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
            opera.types.forEach(element => {
                element.replace(/'/g, '&#39;').replace(/"/g, '&quot;')
            })
            // Escaping delle stringhe per evitare problemi con caratteri speciali
            const safeName = (opera.name || 'Nome non disponibile').replace(/'/g, '&#39;').replace(/"/g, '&quot;');
            const safeType = (opera.types.join(", "));
            const safeMuseum = (opera.museum || 'Museo non specificato').replace(/'/g, '&#39;').replace(/"/g, '&quot;');
            const safeDescription = (opera.description || 'Nessuna descrizione').replace(/'/g, '&#39;').replace(/"/g, '&quot;');
            const safeImageUrl = opera.imageurl ? opera.imageurl.replace(/'/g, '&#39;').replace(/"/g, '&quot;') : '';
            const safeId = opera._id ? opera._id.replace(/'/g, '&#39;').replace(/"/g, '&quot;') : '';
            
            return `
                <div class="opera-card">
                    <h3>${safeName}</h3>
                    <p><strong>Esposta al pubblico:</strong> ${opera.is_exposed ? "Si":"No"}</p>
                    <p><strong>Tipi:</strong> ${safeType}</p>
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


function displayRewards(rewards) {
    const grid = document.getElementById('rewardsGrid');
    
    if (!grid) {
        console.error('Elemento rewardsGrid non trovato');
        return;
    }
    
    if (!rewards || rewards.length === 0) {
        grid.innerHTML = '<div class="loading">Nessuna opera trovata</div>';
        return;
    }
    
    try { 
        const rewardCards = rewards.map(reward => {

        //     "_id": "68c9ed249f316c5b8379c748",
        //     "created_at": "2025-09-16T23:05:08.831863",
        //     "data": {
        //         "amount": "14",
        //         "reduction_type": "percentage"
        //     },
        //     "description": "E' peppe bove",
        //     "expiration_date": "Sat, 27 Sep 2025 00:00:00 GMT",
        //     "museum_id": "68c9a5d91ab37349b555e5fe",
        //     "subject": "Peppe Bove"
        // }
            // Escaping delle stringhe per evitare problemi con caratteri speciali
            const safeSubject = (reward.subject).replace(/'/g, '&#39;').replace(/"/g, '&quot;');
            const safeStock = (reward.stock)
            const safecreated_at = (reward.created_at).replace(/'/g, '&#39;').replace(/"/g, '&quot;');
            const safeamount = (reward.reduction.amount);
            const safereduction = (reward.reduction.type);
            const safedescription = reward.description.replace(/'/g, '&#39;').replace(/"/g, '&quot;');
            const safeexpiration_date = reward.expiration_date.replace(/'/g, '&#39;').replace(/"/g, '&quot;');
            const safeId = reward._id ? reward._id.replace(/'/g, '&#39;').replace(/"/g, '&quot;') : '';
            return `
                <div class="opera-card">
                    <h3>${safeSubject}</h3>
                    <p><strong>Stock :</strong> ${safeStock}</p>
                    <p><strong>Creata il:</strong> ${safecreated_at}</p>
                    <p><strong>Descrizione:</strong> ${safedescription}</p>
                    <p><strong>Riduzione:</strong> ${safereduction}</p>
                    <p><strong>Ammontare:</strong> ${safeamount}</p>
                    <p><strong>Scadenza:</strong> ${safeexpiration_date}</p>
                    <div class="opera-actions">
                        <button class="btn-edit" )">Modifica</button>
                        <button class="btn-delete" onclick="deleteReward('${safeId}')")">Elimina</button>
                    </div>
                </div>
            `;
        });
        
        grid.innerHTML = rewardCards.join('');
    } catch (error) {
        console.error('Errore nella generazione delle carte rewards:', error);
        grid.innerHTML = '<div class="loading">Errore nella visualizzazione delle rewards</div>';
    }
}

async function deleteReward(id){
    if (!confirm('Sei sicuro di voler eliminare questa reward? Questa azione non può essere annullata.')) {
        return;
    }
    
    try {
        const response = await authorizedFetch(`${REWARD_API}/${id}`, {
            method: 'DELETE'
        });
        
        if (response.ok) {
            showAlert('rewardAlert', 'Opera eliminata con successo!', 'success');
            loadRewards();
        } else {
            showAlert('rewardAlert', 'Errore nell\'eliminazione dell\'opera', 'error');
        }
    } catch (error) {
        showAlert('rewardAlert', 'Errore di connessione al server', 'error');
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
    var types = document.getElementById('addType').value.trim().split(',')
    types.forEach(t=>{return t.trim()})
    const operaData = {
        name: document.getElementById('addNome').value.trim(),
        types: types,
        description: document.getElementById('addDescrizione').value.trim(),
        museum: localStorage.getItem("museum_id"),
        imageurl: document.getElementById('addImageurl').value.trim(),
        is_exposed: document.getElementById('isExposed').checked,
    };
    
    try {
        const response = await authorizedFetch(`${OPERE_API}/`, {
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

document.getElementById('addRewardForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    var data ={
        "type": document.getElementById('percentage').checked ? "percentage":"flat",
        "amount":parseFloat(document.getElementById('addAmount').value,10)
    }
    const rewardData = {
        subject: document.getElementById('addSubject').value.trim(),
        expiration_date: new Date(document.getElementById('addExpiration').value).toISOString(),
        description: document.getElementById('addDescription').value.trim(),
        reduction: data,
        stock: parseInt(document.getElementById('addStock').value,10)
    };
    
    try {
        const response = await authorizedFetch(`${REWARD_API}/${localStorage.getItem("museum_id")}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(rewardData)
        });
        
        if (response.ok) {
            showAlert('addRewardAlert', 'Reward aggiunta con successo!', 'success');
            // clearAddForm();
            // loadOpere();
            // loadOpereForSelect();
        } else {
            showAlert('addRewardAlert', 'Errore nell\'aggiunta del Reward', 'error');
        }
    } catch (error) {
        showAlert('addRewardAlert', 'Errore di connessione al server', 'error');
    }
});

// Load opere for select dropdowns
async function loadOpereForSelect() {
    try {
        const response = await authorizedFetch(`${OPERE_API}/search?museum=${encodeURIComponent(localStorage.getItem("museum_id"))}`);
        const result = await response.json();
        
        // Gestisce la struttura della risposta del server
        const opere = result.data || result;
        
        const operaSelect = document.getElementById('operaSelect');
        const deleteOperaSelect = document.getElementById('deleteOperaSelect');
        
        const options = opere.map(opera => 
            `<option value="${opera._id}">${opera.name}</option>`
        ).join('');
        
        operaSelect.innerHTML = '<option value="">Seleziona un\'opera</option>' + options;
        deleteOperaSelect.innerHTML = '<option value="">Seleziona un\'opera</option>' + options;
    } catch (error) {
        console.error('Errore nel caricamento delle opere per select:', error);
    }
}

// Clear modify museum form
function clearModifyMuseumForm() {
    document.getElementById('modifyMuseumForm').reset();
    document.getElementById('modifyMuseumAlert').innerHTML = '';
    loadMuseumForEdit(); // Reload original data
}


// Load museum info for editing
function loadMuseumForEdit() {
    if (!currentMuseum) return;
    
    document.getElementById('modMuseumName').value = currentMuseum.name || '';
    document.getElementById('modMuseumDescription').value = currentMuseum.description || '';
    document.getElementById('modMuseumType').value = currentMuseum.types.join(", ") || '';
    document.getElementById('modMuseumHours').value = currentMuseum.hours || '';
    document.getElementById('modMuseumPrice').value = currentMuseum.price || '';
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
        types: type,
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
        authorizedFetch(`${MUSEUM_API}/${currentMuseum._id}`, 'PUT', museumData);
        
        const response = await authorizedFetch(`${MUSEUM_API}/modifymuseum/${currentMuseum._id}`, {
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

function searchReward(){
    console.log("Search")
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

// Initialize tabs on page load
document.addEventListener('DOMContentLoaded', function() {
    // Set up click handlers for tabs
    document.querySelectorAll('.tab').forEach((tab, index) => {
        tab.addEventListener('click', function() {
            const tabNames = ['opere', 'reward','aggiungi','aggiungiReward', 'modifica', 'elimina', 'modificaMuseo'];
            showTab(tabNames[index]);
        });
    });
});

// Handle Enter key in search
document.getElementById('searchInput').addEventListener('keypress', function(e) {
    if (e.key === 'Enter') {
        searchOpere();
    }
});


