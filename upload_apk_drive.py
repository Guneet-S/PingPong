import time
import os
import subprocess
import sys
import shutil
from playwright.sync_api import sync_playwright

APK_PATH = r"C:\Users\Administrator\PingPong\app\build\outputs\apk\debug\app-debug.apk"
BRAVE_EXE = r"C:\Program Files\BraveSoftware\Brave-Browser\Application\brave.exe"
USER_DATA_DIR = r"C:\Users\Administrator\AppData\Local\BraveSoftware\Brave-Browser\User Data"
TEMP_USER_DATA = r"C:\Users\Administrator\AppData\Local\BraveSoftware\Brave-Browser\User Data-playwright"
SCREENSHOT_DIR = r"C:\Users\Administrator\PingPong"

def take_screenshot(page, name):
    path = os.path.join(SCREENSHOT_DIR, f"{name}.png")
    page.screenshot(path=path, full_page=False)
    print(f"Screenshot saved: {path}")
    return path

CDP_PORT = 9222

# Copy the Default profile to a temp dir so we get login cookies
# but don't conflict with existing Brave instance
print("Preparing profile copy with login cookies...")
temp_default = os.path.join(TEMP_USER_DATA, "Default")
src_default = os.path.join(USER_DATA_DIR, "Default")

if os.path.exists(TEMP_USER_DATA):
    shutil.rmtree(TEMP_USER_DATA, ignore_errors=True)
os.makedirs(TEMP_USER_DATA, exist_ok=True)

# Copy only essential cookie/login files
essential_files = ["Cookies", "Login Data", "Web Data", "Local State",
                   "Preferences", "Secure Preferences", "Network Action Predictor"]
os.makedirs(temp_default, exist_ok=True)

for f in essential_files:
    src = os.path.join(src_default, f)
    dst = os.path.join(temp_default, f)
    if os.path.exists(src):
        try:
            shutil.copy2(src, dst)
            print(f"  Copied: {f}")
        except Exception as e:
            print(f"  Could not copy {f}: {e}")

# Also copy Local Storage and IndexedDB for Google auth tokens
for folder in ["Local Storage", "Session Storage", "Extension Cookies"]:
    src_folder = os.path.join(src_default, folder)
    dst_folder = os.path.join(temp_default, folder)
    if os.path.exists(src_folder):
        try:
            shutil.copytree(src_folder, dst_folder)
            print(f"  Copied folder: {folder}")
        except Exception as e:
            print(f"  Could not copy folder {folder}: {e}")

# Copy Local State for encryption keys
src_ls = os.path.join(USER_DATA_DIR, "Local State")
dst_ls = os.path.join(TEMP_USER_DATA, "Local State")
if os.path.exists(src_ls):
    try:
        shutil.copy2(src_ls, dst_ls)
        print("  Copied Local State")
    except Exception as e:
        print(f"  Could not copy Local State: {e}")

print(f"\nLaunching Brave with temp profile and CDP port {CDP_PORT}...")
proc = subprocess.Popen([
    BRAVE_EXE,
    f"--remote-debugging-port={CDP_PORT}",
    f"--user-data-dir={TEMP_USER_DATA}",
    "--no-first-run",
    "--no-default-browser-check",
    "--disable-sync",
    "about:blank"
], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

print("Waiting for Brave to start...")
time.sleep(5)

# Try to connect with retries
connected = False
for attempt in range(5):
    try:
        import urllib.request
        urllib.request.urlopen(f"http://localhost:{CDP_PORT}/json", timeout=3)
        connected = True
        print(f"CDP endpoint ready (attempt {attempt+1})")
        break
    except Exception:
        print(f"Attempt {attempt+1}: CDP not ready yet, waiting...")
        time.sleep(2)

if not connected:
    print("Could not reach CDP endpoint")
    proc.terminate()
    sys.exit(1)

with sync_playwright() as p:
    print("Connecting to Brave via CDP...")
    browser = p.chromium.connect_over_cdp(f"http://localhost:{CDP_PORT}")
    print("Connected!")

    contexts = browser.contexts
    if contexts:
        context = contexts[0]
    else:
        context = browser.new_context()

    page = context.new_page()

    print("Navigating to Google Drive...")
    page.goto("https://drive.google.com", wait_until="networkidle", timeout=60000)
    time.sleep(3)

    take_screenshot(page, "01_drive_loaded")

    current_url = page.url
    page_title = page.title()
    print(f"URL: {current_url}")
    print(f"Title: {page_title}")

    if "accounts.google.com" in current_url or "signin" in current_url.lower():
        take_screenshot(page, "02_not_logged_in")
        print("NOT LOGGED IN - Google sign-in page detected")
        print("Cannot proceed without login. Check screenshot 02_not_logged_in.png")
        browser.close()
        proc.terminate()
        sys.exit(1)

    print("Logged in to Google Drive!")
    take_screenshot(page, "02_logged_in")

    # Find and click the New button
    print("Looking for New button...")
    new_clicked = False
    selectors_to_try = [
        '[data-tooltip="New"]',
        '.UywwFc-LgbsSe-lR6oPb',
        'button[aria-label*="New"]',
        'div[role="button"]:has-text("New")',
    ]
    for sel in selectors_to_try:
        try:
            btn = page.wait_for_selector(sel, timeout=5000)
            btn.click()
            new_clicked = True
            print(f"Clicked New button via: {sel}")
            break
        except Exception:
            pass

    if not new_clicked:
        # Try text-based click
        try:
            page.get_by_role("button", name="New").click()
            new_clicked = True
            print("Clicked New via get_by_role")
        except Exception as e:
            print(f"get_by_role failed: {e}")

    if not new_clicked:
        print("Could not find New button, inspecting page...")
        take_screenshot(page, "03_no_new_button")
        # Print page structure
        all_buttons = page.locator('button, [role="button"]').all_text_contents()
        print(f"Buttons/clickables: {all_buttons[:30]}")
        browser.close()
        proc.terminate()
        sys.exit(1)

    time.sleep(2)
    take_screenshot(page, "03_new_menu_open")

    # Click File upload from the dropdown
    print("Looking for File upload option...")
    file_upload_clicked = False
    fu_selectors = [
        'text=File upload',
        '[aria-label="File upload"]',
        'span:has-text("File upload")',
    ]
    for sel in fu_selectors:
        try:
            item = page.wait_for_selector(sel, timeout=5000)
            item.click()
            file_upload_clicked = True
            print(f"Clicked File upload via: {sel}")
            break
        except Exception:
            pass

    if not file_upload_clicked:
        print("File upload not found, inspecting menu...")
        menu_items = page.locator('[role="menuitem"], [role="option"]').all_text_contents()
        print(f"Menu items: {menu_items}")
        take_screenshot(page, "04_no_file_upload")
        browser.close()
        proc.terminate()
        sys.exit(1)

    time.sleep(0.5)
    take_screenshot(page, "04_file_chooser_moment")

    # Handle the file chooser
    print("Handling file chooser...")
    try:
        with page.expect_file_chooser(timeout=10000) as fc_info:
            time.sleep(0.3)
        fc = fc_info.value
        print(f"File chooser open, setting file: {APK_PATH}")
        fc.set_files(APK_PATH)
        print("File set!")
    except Exception as e:
        print(f"File chooser (first attempt) issue: {e}")
        # The chooser may have already appeared; try triggering file upload again
        try:
            with page.expect_file_chooser(timeout=10000) as fc_info:
                # Re-open New menu and click File upload
                page.keyboard.press("Escape")
                time.sleep(0.5)
                page.get_by_role("button", name="New").click()
                time.sleep(1)
                page.click('text=File upload')
            fc = fc_info.value
            fc.set_files(APK_PATH)
            print("File set on retry!")
        except Exception as e2:
            print(f"File chooser retry failed: {e2}")
            take_screenshot(page, "05_chooser_fail")
            browser.close()
            proc.terminate()
            sys.exit(1)

    print("File submitted. Waiting for upload...")
    time.sleep(5)
    take_screenshot(page, "05_uploading")

    # Wait for upload to complete
    upload_complete = False
    for i in range(15):
        try:
            if page.locator('text=Upload complete').count() > 0:
                print("Upload complete!")
                upload_complete = True
                break
        except Exception:
            pass
        try:
            if page.locator('[aria-label*="Upload complete"]').count() > 0:
                print("Upload complete (aria-label)!")
                upload_complete = True
                break
        except Exception:
            pass
        time.sleep(4)
        if i % 3 == 0:
            take_screenshot(page, f"05_waiting_{i}")

    take_screenshot(page, "06_after_upload")

    # Navigate to My Drive
    print("Going to My Drive...")
    page.goto("https://drive.google.com/drive/my-drive", wait_until="networkidle", timeout=30000)
    time.sleep(3)
    take_screenshot(page, "07_my_drive")

    # Find the uploaded file
    print("Finding app-debug.apk...")
    shareable_link = None
    file_id = None

    try:
        file_item = page.wait_for_selector(
            '[data-filename="app-debug.apk"], [title="app-debug.apk"], '
            'text=app-debug.apk, [aria-label*="app-debug"]',
            timeout=15000
        )
        print("Found APK file!")
        take_screenshot(page, "08_file_found")

        # Right-click for context menu
        file_item.click(button="right")
        time.sleep(1.5)
        take_screenshot(page, "09_context_menu")

        # Print menu items
        menu_items = page.locator('[role="menuitem"]').all_text_contents()
        print(f"Context menu items: {menu_items}")

        # Try "Get link" first, then "Share"
        link_clicked = False
        for opt in ['text=Get link', 'text=Share', '[aria-label*="Get link"]']:
            try:
                el = page.wait_for_selector(opt, timeout=3000)
                el.click()
                link_clicked = True
                print(f"Clicked: {opt}")
                break
            except Exception:
                pass

        if not link_clicked:
            print("Neither Get link nor Share found in context menu")
            take_screenshot(page, "10_no_share_option")
        else:
            time.sleep(2)
            take_screenshot(page, "10_share_dialog")

            # Try to copy link
            try:
                copy_btn = page.wait_for_selector('text=Copy link', timeout=5000)
                copy_btn.click()
                print("Clicked Copy link")
                time.sleep(1)
                take_screenshot(page, "11_link_copied")
            except Exception as e:
                print(f"Copy link not found: {e}")

            # Extract link from dialog
            import re
            page_content = page.content()

            # Look for file share URL in page
            patterns = [
                r'https://drive\.google\.com/file/d/([A-Za-z0-9_-]+)',
                r'https://docs\.google\.com/[^\s"<>]+',
            ]
            for pat in patterns:
                matches = re.findall(pat, page_content)
                if matches:
                    if '/file/d/' in pat:
                        file_id = matches[0]
                        shareable_link = f"https://drive.google.com/file/d/{file_id}/view?usp=sharing"
                    else:
                        shareable_link = matches[0]
                    print(f"Extracted file ID: {file_id}")
                    print(f"SHAREABLE LINK: {shareable_link}")
                    break

            # Also check input fields
            if not shareable_link:
                inputs = page.locator('input').all()
                for inp in inputs:
                    try:
                        val = inp.get_attribute("value") or ""
                        if "drive.google.com" in val or "docs.google.com" in val:
                            shareable_link = val
                            print(f"SHAREABLE LINK (input): {shareable_link}")
                            break
                    except Exception:
                        pass

    except Exception as e:
        print(f"Could not find APK in My Drive: {e}")
        take_screenshot(page, "08_file_not_found")

        # As a fallback, search for the file
        print("Trying to search for file...")
        try:
            page.goto("https://drive.google.com/drive/search?q=app-debug.apk", wait_until="networkidle")
            time.sleep(3)
            take_screenshot(page, "08_search_results")
            file_item = page.wait_for_selector('text=app-debug.apk, text=app-debug', timeout=10000)
            print("Found file via search!")
            take_screenshot(page, "08b_search_found")
        except Exception as e2:
            print(f"Search also failed: {e2}")

    take_screenshot(page, "12_final")

    print("\n=== RESULT ===")
    if shareable_link:
        print(f"Upload SUCCESSFUL!")
        print(f"Shareable link: {shareable_link}")
    else:
        print("Could not confirm upload or extract shareable link.")
        print("Check screenshots in:", SCREENSHOT_DIR)

    time.sleep(2)
    browser.close()

proc.terminate()

# Cleanup temp profile
print("\nCleaning up temp profile...")
shutil.rmtree(TEMP_USER_DATA, ignore_errors=True)

print("Done!")
