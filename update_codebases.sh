generate_patch_file() {
  cd $1/ || exit
  git diff --relative=$1/ > ../patchfile.patch
  cd ..
}
apply_patch_file() {
  cd $1/ || exit
  patch -N -p1 < ../patchfile.patch
  find . -name "*.orig" -type f -delete
  find . -name "*.rej" -type f -delete
  cd ..
}

generate_patch_file "mc-1-20-1-fabric"
apply_patch_file "mc-1-20-2-fabric"
rm patchfile.patch
