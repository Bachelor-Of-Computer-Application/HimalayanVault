const fs = require('fs');

const files = [
  'src/main/resources/css/login.css',
  'src/main/resources/css/signup.css'
];

files.forEach(file => {
  let css = fs.readFileSync(file, 'utf8');
  css = css.replace(
    /-fx-background-color:\s*linear-gradient\([^)]+\);/,
    '-fx-background-color: linear-gradient(to bottom right, rgba(10, 8, 18, 0.7) 0%, rgba(21, 17, 30, 0.7) 50%, rgba(10, 8, 18, 0.7) 100%);'
  );
  fs.writeFileSync(file, css);
});
