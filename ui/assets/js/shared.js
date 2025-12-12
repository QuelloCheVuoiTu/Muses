const HOSTNAME = "__MUSES_HOSTNAME__"
const OPERE_API = HOSTNAME+'/artworks';
const MUSEUM_API = HOSTNAME+'/museums';
const REWARD_API = HOSTNAME+'/reward';
const AUTH_API = HOSTNAME+'/auth';

function updateOptions(options){
    const update = { ...options };
    const token = localStorage.getItem("token") 
    if (token != null && token != "") {
        update.headers = {
            ...update.headers,
            Authorization: `Bearer ${token}`,
            'Access-Control-Allow-Origin': '__MUSES_HOSTNAME__/',
            'Access-Control-Allow-Methods': "GET, POST, PUT, DELETE, OPTIONS",
            'Access-Control-Allow-Headers': "Content-Type, Authorization"
    };
  }
  return update;
}

function authorizedFetch(url, options){
    return fetch(url,updateOptions(options))
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

// Show dashboard
function showDashboard() {
    location.href = "/ui/dashboard.html"
    
    // Aspetta un momento per assicurarsi che il DOM sia pronto
}
