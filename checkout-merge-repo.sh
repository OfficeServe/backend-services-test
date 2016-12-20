mkdir init
cd init
echo "# single-repo-merge-test" >> README.md
git init
git add README.md
git commit -m "first commit"
git remote add origin git@github.com:OfficeServe/single-repo-merge-test.git
git push -u origin master
cd ..

for p in basket-service backend-common-utils common-spray document-service dynamodb-support report-service send-email-lambda; do
# for p in backend-common-utils; do

  mkdir $p; cd $p
  git clone git@github.com:OfficeServe/$p.git
  cd $p; mkdir $p
  git branch $p-import
  git checkout $p-import
  for i in *; do git mv $i $p/; done
  git rm .gitignore
  git commit -m "moved project"
  git remote remove origin
  git remote add origin git@github.com:OfficeServe/single-repo-merge-test.git
  git push --set-upstream origin $p-import
  cd ..; cd ..

done

mkdir merge
cd merge

git clone git@github.com:OfficeServe/single-repo-merge-test.git
cd single-repo-merge-test

git checkout master
git branch merge-all
git checkout merge-all

for p in basket-service backend-common-utils common-spray document-service dynamodb-support report-service send-email-lambda; do
  git merge --no-edit --allow-unrelated-histories origin/$p-import master
done

cd ..

git mv send-email-lambda send-email
