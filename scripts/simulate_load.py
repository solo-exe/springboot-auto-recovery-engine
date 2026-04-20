import requests
import random
import time
import logging
import uuid

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

BASE_URL = "http://localhost:8080"
TEST_PASSWORD = "Password123!"

class MicroserviceClient:
    def __init__(self, email):
        self.email = email
        self.session = requests.Session()
        self.token = None
        self.user_id = None
        self.account_number = None

    def signup(self):
        logger.info(f"Signing up user: {self.email}")
        payload = {
            "firstName": "Test",
            "lastName": "User",
            "email": self.email,
            "phoneNumber": "08012345678"
        }
        resp = self.session.post(f"{BASE_URL}/api/accounts/auth/signup", json=payload)
        resp.raise_for_status()
        data = resp.json()
        self.user_id = data['data']['id']
        logger.info(f"User signed up with ID: {self.user_id}")

    def activate(self):
        # Use the master OTP "123456" for testing
        logger.info(f"Activating user: {self.email}")
        
        # Step 1: Verify OTP (using master 123456)
        # AuthController /verify-otp expects VerifyOtpRequest: userId, email, otp
        verify_payload = {
            "userId": self.user_id,
            "email": self.email,
            "otp": "123456"
        }
        resp = self.session.post(f"{BASE_URL}/api/accounts/auth/verify-otp", json=verify_payload)
        resp.raise_for_status()
        confirmation_id = resp.json()['data']['confirmationId']
        
        # Step 2: Create Password
        # AuthController /create-password expects VerifyOnboardOtpRequest: email, password, confirmationId
        password_payload = {
            "email": self.email,
            "password": TEST_PASSWORD,
            "confirmationId": confirmation_id
        }
        resp = self.session.post(f"{BASE_URL}/api/accounts/auth/create-password", json=password_payload)
        resp.raise_for_status()
        logger.info(f"User {self.email} activated successfully")

    def login(self):
        logger.info(f"Logging in user: {self.email}")
        payload = {
            "email": self.email,
            "password": TEST_PASSWORD
        }
        resp = self.session.post(f"{BASE_URL}/api/accounts/auth/login", json=payload)
        resp.raise_for_status()
        data = resp.json()
        self.token = data['data']['token']
        self.session.headers.update({"Authorization": f"Bearer {self.token}"})
        logger.info(f"User {self.email} logged in")

    def get_account_details(self):
        # Using AccountController /accounts (proxied as /api/accounts)
        resp = self.session.get(f"{BASE_URL}/api/accounts")
        resp.raise_for_status()
        accounts = resp.json()['data']['content']
        # Find account for this user using email
        for acc in accounts:
            if acc['email'] == self.email:
                self.account_number = acc['accountNumber']
                return acc
        return None

    def fund_account(self, amount):
        # Using AccountController PUT /accounts/{id}/balance
        # We need the inner account ID, not user ID.
        acc = self.get_account_details()
        if not acc: return
        
        logger.info(f"Funding account {self.account_number} with {amount}")
        payload = {
            "amount": amount,
            "type": "CREDIT",
            "description": "Simulation funding"
        }
        resp = self.session.put(f"{BASE_URL}/api/accounts/{acc['id']}/balance", json=payload)
        resp.raise_for_status()

    def initiate_payment(self, destination, amount):
        logger.info(f"Initiating payment of {amount} to {destination}")
        payload = {
            "destinationAccountNumber": destination,
            "amount": amount,
            "description": "Simulation payment"
        }
        resp = self.session.post(f"{BASE_URL}/api/payments/initiate", json=payload)
        return resp

    def get_history(self):
        resp = self.session.get(f"{BASE_URL}/api/payments/history")
        resp.raise_for_status()
        return resp.json()['data']

def run_simulation(duration_seconds=300):
    users = []
    # Create 3 test users with retries
    for i in range(3):
        email = f"sim_user_{uuid.uuid4().hex[:8]}@example.com"
        client = MicroserviceClient(email)
        
        setup_success = False
        for attempt in range(5):
            try:
                logger.info(f"Setting up user {email} (Attempt {attempt+1}/5)")
                client.signup()
                client.activate()
                client.login()
                client.fund_account(50000.00)
                users.append(client)
                setup_success = True
                break
            except Exception as e:
                logger.error(f"Failed to setup user {email}: {e}")
                time.sleep(5)
                
        if not setup_success:
            logger.error(f"Giving up on user {email}")

    if not users:
        logger.error("No users successfully created. Exiting.")
        return

    start_time = time.time()
    while time.time() - start_time < duration_seconds:
        user = random.choice(users)
        action = random.choice(["history", "payment", "details"])
        
        try:
            if action == "history":
                user.get_history()
                logger.info(f"User {user.email} checked history")
            elif action == "details":
                user.get_account_details()
                logger.info(f"User {user.email} checked details")
            elif action == "payment":
                # Pay to another simulation user or a dummy account
                dest = "1234567890" 
                if len(users) > 1:
                    other_user = random.choice([u for u in users if u != user])
                    other_user.get_account_details() # ensure we have their number
                    if other_user.account_number:
                        dest = other_user.account_number
                
                resp = user.initiate_payment(dest, random.uniform(10, 100))
                if resp.status_code >= 400:
                    logger.warning(f"Payment failed for {user.email}: {resp.status_code}")
                else:
                    logger.info(f"User {user.email} made payment to {dest}")
        except Exception as e:
            logger.error(f"Error during simulation for {user.email}: {e}")

        time.sleep(random.uniform(1, 4))

if __name__ == "__main__":
    run_simulation()
