#!/usr/bin/env python3
"""
Script to create Android launcher icons from a base PNG image
This script will create the appropriate icon sizes for Android
"""

from PIL import Image, ImageDraw, ImageFont
import os

def create_icon_base():
    """Create the base 512x512 icon matching the reference design"""
    # Create a 512x512 image with purple gradient
    size = 512
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Create purple gradient background (matching reference)
    for y in range(size):
        # Gradient from #9333EA to #7C3AED
        ratio = y / size
        r = int(147 * (1 - ratio) + 124 * ratio)  # 147 -> 124
        g = int(51 * (1 - ratio) + 58 * ratio)    # 51 -> 58  
        b = int(234 * (1 - ratio) + 237 * ratio)  # 234 -> 237
        draw.line([(0, y), (size, y)], fill=(r, g, b, 255))
    
    return img

def add_text_to_icon(img):
    """Add DND SCHEDULER text to the icon"""
    draw = ImageDraw.Draw(img)
    size = img.size[0]
    
    # Try to use a bold font, fall back to default if not available
    try:
        # Try different font paths for Windows
        font_paths = [
            "C:/Windows/Fonts/arial.ttf",
            "C:/Windows/Fonts/calibri.ttf", 
            "arial.ttf"
        ]
        
        dnd_font = None
        scheduler_font = None
        
        for font_path in font_paths:
            try:
                dnd_font = ImageFont.truetype(font_path, size // 4)  # Large font for DND
                scheduler_font = ImageFont.truetype(font_path, size // 12)  # Smaller font for SCHEDULER
                break
            except:
                continue
                
        if not dnd_font:
            dnd_font = ImageFont.load_default()
            scheduler_font = ImageFont.load_default()
            
    except:
        dnd_font = ImageFont.load_default()
        scheduler_font = ImageFont.load_default()
    
    # Add DND text (centered, large)
    dnd_text = "DND"
    dnd_bbox = draw.textbbox((0, 0), dnd_text, font=dnd_font)
    dnd_width = dnd_bbox[2] - dnd_bbox[0]
    dnd_height = dnd_bbox[3] - dnd_bbox[1]
    dnd_x = (size - dnd_width) // 2
    dnd_y = (size // 2) - dnd_height - (size // 20)
    
    draw.text((dnd_x, dnd_y), dnd_text, fill=(255, 255, 255, 255), font=dnd_font)
    
    # Add SCHEDULER text (centered, smaller, below DND)
    scheduler_text = "SCHEDULER"
    scheduler_bbox = draw.textbbox((0, 0), scheduler_text, font=scheduler_font)
    scheduler_width = scheduler_bbox[2] - scheduler_bbox[0]
    scheduler_x = (size - scheduler_width) // 2
    scheduler_y = dnd_y + dnd_height + (size // 30)
    
    draw.text((scheduler_x, scheduler_y), scheduler_text, fill=(255, 255, 255, 255), font=scheduler_font)
    
    return img

def create_android_icons():
    """Create all required Android icon sizes"""
    # Create base icon
    base_icon = create_icon_base()
    base_icon = add_text_to_icon(base_icon)
    
    # Save the 512x512 version for Play Store
    base_icon.save("h:/DND-Scheduler/playstore_icon_512.png", "PNG")
    
    # Icon sizes for different densities
    sizes = {
        'mdpi': 48,
        'hdpi': 72,
        'xhdpi': 96,
        'xxhdpi': 144,
        'xxxhdpi': 192
    }
    
    # Create foreground (full icon) and background (just gradient) versions
    for density, size in sizes.items():
        # Foreground (complete icon)
        foreground = base_icon.resize((size, size), Image.Resampling.LANCZOS)
        foreground.save(f"h:/DND-Scheduler/app/src/main/res/mipmap-{density}/ic_launcher_foreground.png", "PNG")
        
        # Background (just gradient)
        background = create_icon_base().resize((size, size), Image.Resampling.LANCZOS)
        background.save(f"h:/DND-Scheduler/app/src/main/res/mipmap-{density}/ic_launcher_background.png", "PNG")
        
        # Also create regular launcher icons
        foreground.save(f"h:/DND-Scheduler/app/src/main/res/mipmap-{density}/ic_launcher.png", "PNG")
        foreground.save(f"h:/DND-Scheduler/app/src/main/res/mipmap-{density}/ic_launcher_round.png", "PNG")

if __name__ == "__main__":
    print("Creating Android launcher icons...")
    create_android_icons()
    print("Icons created successfully!")
