import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

# Set paths
ASSETS_DIR = r"c:\Users\harsh\AndroidStudioProjects\PocketMind\playstore-assets"
OUTPUT_DIR = os.path.join(ASSETS_DIR, "edited")
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Font configurations
FONT_BOLD = r"C:\Windows\Fonts\segoeuib.ttf"
FONT_REGULAR = r"C:\Windows\Fonts\segoeui.ttf"

# Graphic dimensions
CANVAS_W = 1024
CANVAS_H = 500

# Device mockup dimensions (within feature graphic)
MOCKUP_W = 277
MOCKUP_H = 600
ROUND_RADIUS = 24

def generate_background():
    """Generates a beautiful linear gradient background for the feature graphic."""
    # Create 2x2 image and scale it up with bilinear interpolation
    grad_small = Image.new("RGBA", (2, 2))
    
    # Matching colors from screenshots
    c_tl = (8, 8, 14, 255)      # Top-left: Very dark blue-grey
    c_tr = (16, 12, 22, 255)    # Top-right: Very dark purple-grey
    c_bl = (12, 10, 16, 255)    # Bottom-left: Deep charcoal
    c_br = (42, 28, 10, 255)    # Bottom-right: Rich warm amber glow (matches brand)
    
    pixels = grad_small.load()
    pixels[0, 0] = c_tl
    pixels[1, 0] = c_tr
    pixels[0, 1] = c_bl
    pixels[1, 1] = c_br
    
    gradient = grad_small.resize((CANVAS_W, CANVAS_H), Image.Resampling.BILINEAR)
    
    # Add an amber glow behind the phone on the right
    glow_mask = Image.new("L", (CANVAS_W, CANVAS_H), 0)
    glow_draw = ImageDraw.Draw(glow_mask)
    glow_draw.ellipse(
        [CANVAS_W - 500, CANVAS_H // 2 - 400, CANVAS_W + 100, CANVAS_H // 2 + 400],
        fill=45  # ~18% opacity glow
    )
    glow_blurred = glow_mask.filter(ImageFilter.GaussianBlur(120))
    glow_color = Image.new("RGBA", (CANVAS_W, CANVAS_H), (245, 158, 11, 255))
    
    gradient.paste(glow_color, (0, 0), mask=glow_blurred)
    return gradient

def create_rotated_mockup():
    """Creates a rounded-corner device mockup rotated counter-clockwise."""
    ss_path = os.path.join(ASSETS_DIR, "raw_screenshot_chat.png")
    if not os.path.exists(ss_path):
        print("Warning: raw_screenshot_chat.png not found, using generic gray block.")
        ss_img = Image.new("RGBA", (1440, 3120), (30, 30, 40, 255))
    else:
        ss_img = Image.open(ss_path).convert("RGBA")
        
    scaled_ss = ss_img.resize((MOCKUP_W, MOCKUP_H), Image.Resampling.LANCZOS)
    
    # Create mockup canvas with extra space for bezel & shadow
    pad = 40
    mockup_canvas = Image.new("RGBA", (MOCKUP_W + pad*2, MOCKUP_H + pad*2), (0, 0, 0, 0))
    m_draw = ImageDraw.Draw(mockup_canvas)
    
    # Soft drop shadow coordinates relative to padding
    shadow_mask = Image.new("L", (MOCKUP_W + pad*2, MOCKUP_H + pad*2), 0)
    shadow_draw = ImageDraw.Draw(shadow_mask)
    shadow_draw.rounded_rectangle(
        [(pad, pad), (pad + MOCKUP_W, pad + MOCKUP_H)],
        radius=ROUND_RADIUS,
        fill=160
    )
    shadow_blurred = shadow_mask.filter(ImageFilter.GaussianBlur(15))
    shadow_color = Image.new("RGBA", (MOCKUP_W + pad*2, MOCKUP_H + pad*2), (0, 0, 0, 255))
    mockup_canvas.paste(shadow_color, (0, 10), mask=shadow_blurred) # Offset shadow down 10px
    
    # Paste rounded screenshot
    ss_mask = Image.new("L", (MOCKUP_W, MOCKUP_H), 0)
    ss_mask_draw = ImageDraw.Draw(ss_mask)
    ss_mask_draw.rounded_rectangle(
        [(0, 0), (MOCKUP_W, MOCKUP_H)],
        radius=ROUND_RADIUS,
        fill=255
    )
    mockup_canvas.paste(scaled_ss, (pad, pad), mask=ss_mask)
    
    # Draw Bezel
    m_draw.rounded_rectangle(
        [(pad - 4, pad - 4), (pad + MOCKUP_W + 4, pad + MOCKUP_H + 4)],
        radius=ROUND_RADIUS + 4,
        outline=(22, 21, 26),
        width=4
    )
    m_draw.rounded_rectangle(
        [(pad - 1, pad - 1), (pad + MOCKUP_W + 1, pad + MOCKUP_H + 1)],
        radius=ROUND_RADIUS + 1,
        outline=(45, 42, 54),
        width=1
    )
    
    # Rotate the mockup
    # 8 degrees counter-clockwise
    rotated_mockup = mockup_canvas.rotate(8, expand=True, resample=Image.Resampling.BICUBIC)
    return rotated_mockup

def main():
    print("Generating Feature Graphic (1024x500)...")
    
    # 1. Generate background
    canvas = generate_background()
    draw = ImageDraw.Draw(canvas)
    
    # 2. Add rotated screenshot mockup on the right
    rotated_mockup = create_rotated_mockup()
    # Position the mockup so it sits nicely on the right, bleeding off top & bottom
    # Rotated image will have transparent padding, center it vertically
    rx, ry = 620, -100
    canvas.paste(rotated_mockup, (rx, ry), mask=rotated_mockup)
    
    # 3. Add Logo/Icon on the left
    icon_path = os.path.join(ASSETS_DIR, "icon_512.png")
    if os.path.exists(icon_path):
        icon_img = Image.open(icon_path).convert("RGBA")
        icon_scaled = icon_img.resize((120, 120), Image.Resampling.LANCZOS)
        # Use alpha channel as mask
        canvas.paste(icon_scaled, (80, 75), mask=icon_scaled)
    else:
        print("Warning: icon_512.png not found. Skipping icon paste.")
        
    # 4. Add typography
    font_title = ImageFont.truetype(FONT_BOLD, 68)
    font_tagline1 = ImageFont.truetype(FONT_REGULAR, 32)
    font_tagline2 = ImageFont.truetype(FONT_REGULAR, 26)
    
    # App Name
    draw.text((80, 215), "PocketShadow", font=font_title, fill=(255, 255, 255, 255))
    
    # Taglines
    draw.text((80, 315), "Fully Offline. 100% Private.", font=font_tagline1, fill=(245, 158, 11, 255)) # Brand Amber
    draw.text((80, 365), "On-device AI assistant that never leaves your phone.", font=font_tagline2, fill=(180, 180, 195, 255))
    
    # Save the feature graphic to both the main directory and the edited directory
    output_path_edited = os.path.join(OUTPUT_DIR, "feature_graphic.png")
    output_path_assets = os.path.join(ASSETS_DIR, "feature_graphic.png")
    
    canvas.save(output_path_edited, "PNG")
    canvas.save(output_path_assets, "PNG")
    
    print(f"Saved: {output_path_edited}")
    print(f"Saved: {output_path_assets}")
    print("Feature Graphic generation completed successfully!")

if __name__ == "__main__":
    main()
