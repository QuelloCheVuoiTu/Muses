from firebase_admin import credentials, messaging, initialize_app

cred = credentials.Certificate('/etc/config/firebase-config.json')

initialize_app(cred)


def send_fcm_notification(token, title, body):
    message = messaging.Message(
        notification=messaging.Notification(
            title=title,
            body=body,
        ),
        token=token
    )
    try:
        response = messaging.send(message)
        print('Notification sent successfully!', response)
        return {"success": True, "response": response}
    except Exception as e:
        # Log and return structured error information to the caller
        print('Error sending notification: ', e)
        return {"success": False, "error": str(e)}
        

def request_location_from_device(token):
    """
    Sends a data-only FCM message to a device to request its location.

    :param token: The FCM registration token of the target device.
    :return: A dictionary with the result of the operation.
    """
    message = messaging.Message(
        data={
            'type': 'get_location',
        },
        token=token,
    )

    try:
        response = messaging.send(message)
        print('Location request sent successfully!', response)
        return {"success": True, "response": response}
    except Exception as e:
        print('Error sending location request: ', e)
        return {"success": False, "error": str(e)}
