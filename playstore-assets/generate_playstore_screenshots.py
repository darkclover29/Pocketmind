import os
import glob
from PIL import Image, ImageDraw, ImageFilter, ImageFont

# Set paths
ASSETS_DIR = r"c:\Users\harsh\AndroidStudioProjects\PocketMind\playstore-assets"
OUTPUT_DIR = os.path.join(ASSETS_DIR, "edited")
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Font configurations
FONT_BOLD = r"C:\Windows\Fonts\segoeuib.ttf"
FONT_REGULAR = r"C:\Windows\Fonts\segoeui.ttf"

# Screen dimensions
CANVAS_W = 1440
CANVAS_H = 3120
SS_W = 1080  # 75% of 1440
SS_H = 2340  # 75% of 3120

# Alignment parameters
SS_X = (CANVAS_W - SS_W) // 2
SS_Y = 640
ROUND_RADIUS = 50

# Screenshot definitions: (filename, title, description)
SCREENSHOTS = [
    (
        "raw_screenshot_home.png",
        "TRUE ON-DEVICE AI",
        "Chat with powerful language models directly on your phone. Offline & secure."
    ),
    (
        "raw_screenshot_chat.png",
        "100% OFFLINE & PRIVATE",
        "Your conversations never leave your device. No internet connection required."
    ),
    (
        "raw_screenshot_onboarding.png",
        "NO ACCOUNT REQUIRED",
        "No sign-in, no tracking, and zero data collection. Pure offline utility."
    ),
    (
        "raw_screenshot_features.png",
        "LOCAL INTELLIGENCE",
        "Summarize, explain, rewrite, or translate message bubbles instantly."
    ),
    (
        "raw_screenshot_history.png",
        "SECURE CHAT HISTORY",
        "Search and organize your past conversations. Stored 100% locally."
    ),
    (
        "raw_screenshot_settings.png",
        "CUSTOMIZE YOUR CHAT",
        "Choose from 5 gorgeous themes and customize AI memory settings."
    )
]

def generate_diagonal_gradient():
    """Generates a beautiful high-res background gradient from top-left to bottom-right."""
    # Create a tiny 2x2 image with the corner colors
    # TL: Deep black-navy, TR: Slightly lighter navy, BL: Deep charcoal, BR: Deep amber-gold glow
    grad_small = Image.new("RGBA", (2, 2))
    
    # Define colors
    c_tl = (8, 8, 14, 255)      # Top-left: Very dark blue-grey
    c_tr = (16, 12, 22, 255)    # Top-right: Very dark purple-grey
    c_bl = (12, 10, 16, 255)    # Bottom-left: Deep charcoal
    c_br = (42, 28, 10, 255)    # Bottom-right: Rich warm amber glow (matches brand)
    
    pixels = grad_small.load()
    pixels[0, 0] = c_tl
    pixels[1, 0] = c_tr
    pixels[0, 1] = c_bl
    pixels[1, 1] = c_br
    
    # Scale up using bilinear interpolation for a super smooth gradient
    gradient = grad_small.resize((CANVAS_W, CANVAS_H), Image.Resampling.BILINEAR)
    
    # Add a subtle grid/pattern or noise overlay for premium feel
    overlay = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    
    # Draw subtle background circular glows to highlight the device
    # Glow behind the phone
    glow_mask = Image.new("L", (CANVAS_W, CANVAS_H), 0)
    glow_draw = ImageDraw.Draw(glow_mask)
    glow_draw.ellipse(
        [CANVAS_W // 2 - 600, CANVAS_H // 2 - 300, CANVAS_W // 2 + 600, CANVAS_H // 2 + 900],
        fill=25  # 10% opacity glow
    )
    glow_blurred = glow_mask.filter(ImageFilter.GaussianBlur(150))
    glow_color = Image.new("RGBA", (CANVAS_W, CANVAS_H), (245, 158, 11, 255)) # Amber glow
    
    gradient.paste(glow_color, (0, 0), mask=glow_blurred)
    return gradient

def wrap_text(text, font, max_width, draw):
    """Wraps text to fit within a maximum width."""
    words = text.split(" ")
    lines = []
    current_line = []
    
    for word in words:
        test_line = " ".join(current_line + [word]) if current_line else word
        bbox = draw.textbbox((0, 0), test_line, font=font)
        w = bbox[2] - bbox[0]
        if w <= max_width:
            current_line.append(word)
        else:
            lines.append(" ".join(current_line))
            current_line = [word]
            
    if current_line:
        lines.append(" ".join(current_line))
    return lines

def create_playstore_screenshot(raw_name, title, desc, index):
    raw_path = os.path.join(ASSETS_DIR, raw_name)
    if not os.path.exists(raw_path):
        print(f"Warning: {raw_name} not found. Skipping.")
        return
        
    print(f"Processing: {raw_name} -> screenshot_{index}.png")
    
    # 1. Load raw screenshot and background
    ss_img = Image.open(raw_path).convert("RGBA")
    scaled_ss = ss_img.resize((SS_W, SS_H), Image.Resampling.LANCZOS)
    
    canvas = generate_diagonal_gradient()
    draw = ImageDraw.Draw(canvas)
    
    # 2. Draw Soft Drop Shadow behind the phone
    shadow_mask = Image.new("L", (SS_W + 120, SS_H + 120), 0)
    shadow_draw = ImageDraw.Draw(shadow_mask)
    # Draw rounded rectangle in mask for the shadow base
    shadow_draw.rounded_rectangle(
        [(60, 60), (60 + SS_W, 60 + SS_H)],
        radius=ROUND_RADIUS,
        fill=200  # opacity of shadow
    )
    shadow_blurred = shadow_mask.filter(ImageFilter.GaussianBlur(35))
    shadow_color = Image.new("RGBA", (SS_W + 120, SS_H + 120), (0, 0, 0, 255))
    
    # Paste shadow onto background offset downwards by 25px
    canvas.paste(shadow_color, (SS_X - 60, SS_Y - 60 + 25), mask=shadow_blurred)
    
    # 3. Paste Rounded Screenshot
    ss_mask = Image.new("L", (SS_W, SS_H), 0)
    ss_mask_draw = ImageDraw.Draw(ss_mask)
    ss_mask_draw.rounded_rectangle(
        [(0, 0), (SS_W, SS_H)],
        radius=ROUND_RADIUS,
        fill=255
    )
    canvas.paste(scaled_ss, (SS_X, SS_Y), mask=ss_mask)
    
    # 4. Draw Device Bezel/Frame
    # Outer Bezel
    draw.rounded_rectangle(
        [(SS_X - 14, SS_Y - 14), (SS_X + SS_W + 14, SS_Y + SS_H + 14)],
        radius=ROUND_RADIUS + 14,
        outline=(22, 21, 26),
        width=14
    )
    # Inner metal highlight
    draw.rounded_rectangle(
        [(SS_X - 4, SS_Y - 4), (SS_X + SS_W + 4, SS_Y + SS_H + 4)],
        radius=ROUND_RADIUS + 4,
        outline=(45, 42, 54),
        width=4
    )
    
    # 5. Draw Text (Title & Description)
    font_title = ImageFont.truetype(FONT_BOLD, 85)
    font_desc = ImageFont.truetype(FONT_REGULAR, 44)
    
    # Draw Title (centered, Amber/Brand colored)
    title_bbox = draw.textbbox((0, 0), title, font=font_title)
    title_w = title_bbox[2] - title_bbox[0]
    title_x = (CANVAS_W - title_w) // 2
    title_y = 220
    draw.text((title_x, title_y), title, font=font_title, fill=(245, 158, 11, 255)) # Amber color
    
    # Draw Description (centered, wrapped, white/grey colored)
    desc_lines = wrap_text(desc, font_desc, CANVAS_W - 200, draw)
    desc_y_start = 340
    for idx, line in enumerate(desc_lines):
        line_bbox = draw.textbbox((0, 0), line, font=font_desc)
        line_w = line_bbox[2] - line_bbox[0]
        line_x = (CANVAS_W - line_w) // 2
        draw.text((line_x, desc_y_start + idx * 60), line, font=font_desc, fill=(230, 230, 240, 255))
        
    # Save the screenshot
    output_name = f"screenshot_{index}_{raw_name.replace('raw_screenshot_', '')}"
    output_path = os.path.join(OUTPUT_DIR, output_name)
    canvas.save(output_path, "PNG")
    print(f"Saved: {output_path}")

def main():
    print("Starting Play Store Screenshot Generation...")
    for idx, (raw_name, title, desc) in enumerate(SCREENSHOTS, 1):
        create_playstore_screenshot(raw_name, title, desc, idx)
    print("\nScreenshot generation completed successfully!")

if __name__ == "__main__":
    main()
