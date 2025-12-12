// Register museum function
const types = [];


async function registerMuseum() {
    const name = document.getElementById('regName').value.trim();
    const pw = document.getElementById('regPass').value.trim();
    const musName = document.getElementById('name').value.trim();
    const description = document.getElementById('regDescrizione').value.trim();
    const latitude = document.getElementById('regLatitude').value;
    const longitude = document.getElementById('regLongitude').value;
    const hours = document.getElementById('regHours').value.trim();
    const price = document.getElementById('regPrice').value.trim();
    const imageurl = document.getElementById('regImageurl').value.trim();
    const parent = document.getElementById('regParent').value.trim();
    
    if (!name || !description || !pw) {
        showAlert('loginAlert', 'Nome, password e descrizione sono obbligatori', 'error');
        return;
    }
    
    const museumData = {
        name: musName,
        description: description,
        hours: hours,
        price: price,
        types: types,
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

    if (types.length < 1){
        showAlert('loginAlert', 'Almeno un tipo deve essere indicato', 'error');
    }

    try {
        fetch(`${AUTH_API}/register`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'RBAC-Request': 'M_ADMIN'
            },
            body: JSON.stringify(
                {
                    'username': name,
                    'password': pw
                }
            )
        }).then(async resp =>{
            let headers = new Headers();
            headers.set('Authorization', 'Basic ' + btoa(name + ":" + pw));
            headers.set('RBAC-Name', 'M_ADMIN');
            const response = await fetch(`${AUTH_API}/login`,
                {
                    headers : headers,
                }
            );
            if (response.ok)
            {
                const data = await response.json()
                const token = data.token
                localStorage.setItem("token",token)
                authorizedFetch(`${MUSEUM_API}/`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(museumData)
                }).then(async _ =>{
                    const museum_data = await _.json()
                    museum_id = museum_data.InsertedID
                    if (museum_id == null || museum_id == undefined){
                        throw Error("ADD MUSEUM ERROR")
                    }
                    localStorage.setItem("museum_id",museum_id)
                    showAlert('loginAlert', 'Museo registrato con successo!', 'success');
                    showDashboard()
                    clearRegisterForm();
                }).catch(_=>{
                    console.error("ADD MUSEUM ERROR")
                });
            }else{
                throw Error("Login failed")
            }
        }).catch(_ =>{
            showAlert('loginAlert', 'Errore nella registrazione del museo', 'error');
        })
        
    } catch (error) {
        showAlert('loginAlert', 'Errore di connessione al server', 'error');
    }
}

function typeEnterListener(ele){
    if(event.key === 'Enter') {
        addType()      
    }
}
function addType(){
    var element = document.getElementById('regType');
    var typeShower = document.getElementById('regTypeShower');
    var type = element.value.trim();
    if (type == null || type == ""){
        return 
    }
    element.value = "";
    types.push(type);
    typeShower.innerText = types.join(", ")
}

// Clear modify museum form
function clearModifyMuseumForm() {
    document.getElementById('modifyMuseumForm').reset();
    document.getElementById('modifyMuseumAlert').innerHTML = '';
    loadMuseumForEdit(); // Reload original data
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