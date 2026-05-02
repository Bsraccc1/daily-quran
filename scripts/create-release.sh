#!/bin/bash

# Script to create a new release tag and push to GitHub
# This will trigger the GitHub Actions workflow to build and upload APKs

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get current version from build.gradle.kts
CURRENT_VERSION=$(grep "versionName" app/build.gradle.kts | awk -F'"' '{print $2}')

echo -e "${GREEN}Current version: ${CURRENT_VERSION}${NC}"
echo ""

# Ask for new version
read -p "Enter new version (e.g., 9.3.0): " NEW_VERSION

if [ -z "$NEW_VERSION" ]; then
    echo -e "${RED}Error: Version cannot be empty${NC}"
    exit 1
fi

# Confirm
echo ""
echo -e "${YELLOW}This will:${NC}"
echo "  1. Update version in build.gradle.kts to ${NEW_VERSION}"
echo "  2. Commit the version change"
echo "  3. Create and push tag v${NEW_VERSION}"
echo "  4. Trigger GitHub Actions to build and release APKs"
echo ""
read -p "Continue? (y/n): " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${RED}Cancelled${NC}"
    exit 1
fi

# Update version in build.gradle.kts
echo -e "${GREEN}Updating version...${NC}"
sed -i "s/versionName = \".*\"/versionName = \"${NEW_VERSION}\"/" app/build.gradle.kts

# Also update versionCode (increment by 1)
CURRENT_CODE=$(grep "versionCode = " app/build.gradle.kts | awk '{print $3}')
NEW_CODE=$((CURRENT_CODE + 1))
sed -i "s/versionCode = ${CURRENT_CODE}/versionCode = ${NEW_CODE}/" app/build.gradle.kts

echo -e "${GREEN}Version updated to ${NEW_VERSION} (code: ${NEW_CODE})${NC}"

# Commit version change
echo -e "${GREEN}Committing version change...${NC}"
git add app/build.gradle.kts
git commit -m "Bump version to ${NEW_VERSION}"

# Create and push tag
echo -e "${GREEN}Creating tag v${NEW_VERSION}...${NC}"
git tag -a "v${NEW_VERSION}" -m "Release version ${NEW_VERSION}"

echo -e "${GREEN}Pushing to GitHub...${NC}"
git push origin main
git push origin "v${NEW_VERSION}"

echo ""
echo -e "${GREEN}✓ Done!${NC}"
echo ""
echo "GitHub Actions will now build and create a release."
echo "Check: https://github.com/Bsraccc1/daily-quran/actions"
echo ""
