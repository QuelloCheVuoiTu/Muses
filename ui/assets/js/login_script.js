// Login function
async function login() {
    const pw = document.getElementById('museumPw').value.trim();
    const name = document.getElementById('museumName').value.trim();
    
    if (!pw || !name) {
        showAlert('loginAlert', 'Inserisci ID e nome del museo', 'error');
        return;
    }
    
    try {
        // Verify museum exists
        let headers = new Headers();
        headers.set('Authorization', 'Basic ' + btoa(name + ":" + pw));
        headers.set('RBAC-Name', 'M_ADMIN');
        const response = await fetch(`${AUTH_API}/login`,
            {
                headers : headers,
            }
        );
        const logged = response.ok;
        if (! logged) {
            showAlert('loginAlert', 'Login Fallito. Verifica ID e nome.', 'error');
            throw Error("Login unsuccessfull")
        }
        const data = await response.json()
        const token = data.token
        localStorage.setItem("token",token)
        museum_id = response.headers.get("EntityID")
        localStorage.setItem("museum_id",museum_id)
        showDashboard()
    } catch (error) {
        console.error('Errore login:', error);
        showAlert('loginAlert', 'Errore di connessione al server', 'error');
    }
}



function clearLoginForm() {
    document.getElementById('museumId').value = '';
    document.getElementById('museumName').value = '';
    document.getElementById('loginAlert').innerHTML = '';
}