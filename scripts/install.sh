#!/bin/bash
# install.sh
INSTALL_DIR="/usr/local/bin"
echo "Installing RedLab in $INSTALL_DIR..."
sudo cp redlab $INSTALL_DIR/
sudo chmod +x $INSTALL_DIR/redlab
echo "Success! Type 'redlab' to start."