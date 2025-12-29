# Smart Object Detection Assistant

Smart Object Detection Assistant is an Android-based mobile application developed to detect real-world objects in real time using a smartphone camera.  
The application is designed primarily to assist visually impaired users by providing audio feedback using Text-to-Speech (TTS).

---

## 📌 Features
- Real-time object detection using smartphone camera
- Audio feedback for detected objects
- Lightweight and fast on-device inference
- Simple and user-friendly interface
- Designed for visually impaired users

---

## 🧠 Technologies Used
- **Programming Language:** Java  
- **UI Design:** XML  
- **IDE:** Android Studio  
- **Machine Learning Framework:** TensorFlow Lite  
- **Camera Library:** CameraX  
- **Audio Output:** Android Text-to-Speech (TTS)

---

## 📷 Application Screens
The application includes the following screens:
- Splash Screen
- Main Menu
- Camera Detection Screen
- How It Works Screen
- Settings Screen

(Screenshots are available in the project repository)

---

## ⚙️ Machine Learning Model
- **Model Name:** COCO SSD MobileNet V1 (Quantized)
- **Framework:** TensorFlow Lite
- **Input Size:** 300 × 300
- **Dataset:** COCO Dataset
- **Model Type:** Quantized (Optimized for mobile devices)

---

## 🚀 How It Works
1. The camera captures live video frames.
2. Frames are processed using TensorFlow Lite.
3. Objects are detected with confidence scores.
4. Detected object names are converted into audio output using Text-to-Speech.

---

## 📂 Project Structure
SmartObjectDetectionAssistant/
│
├── app/
│ ├── java/
│ ├── res/
│ └── assets/
│ ├── detect.tflite
│ └── labelmap.txt
│
├── screenshots/
├── README.md
└── build.gradle

---

## 🧪 Results
The application successfully detects common objects such as persons, bottles, chairs, and mobile phones in real time.  
Audio feedback is provided with minimal delay.

---

## ⚠️ Limitations
- Detection accuracy depends on lighting conditions
- Small or distant objects may not be detected accurately
- Limited to COCO dataset object categories

---

## 🔮 Future Enhancements
- Urdu language support for Text-to-Speech
- Navigation assistance for visually impaired users
- Distance estimation and object tracking
- Integration of custom-trained models

---

## 👩‍🎓 Author
**Samra Ramzan**  
BS Computer Science  
Kahuta Institute of Space and Technology  

---

## 📜 License
This project is developed for educational purposes.

