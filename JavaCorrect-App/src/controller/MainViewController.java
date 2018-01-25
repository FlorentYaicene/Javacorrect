package controller;

import java.io.File;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import db.Classroom;
import db.MarkingDbManagementException;
import db.MysqlRequest;
import db.Student;
import db.StudentCsvParser;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

public class MainViewController
implements Initializable {
	private static final String PROJECT_CREATION_ERROR = "Impossible de créer le projet";
	private static final String SAVE_CSV_ERROR = "Impossible d'enregistrer le csv";
	private static final String SEND_OUTPUTFILE_ERROR = "Impossible d'envoyer le fichier de sortie au serveur";
	@FXML
	private MenuItem logoutContextMenu;
	@FXML
	private ComboBox<String> projectNameButton;
	@FXML
	private Button deleteProjectButton;
	@FXML
	private TableView<StudentProject> studentProjectsTable;
	@FXML
	private TableColumn<StudentProject, String> studentNameColumn;
	@FXML
	private TableColumn<StudentProject, String> studentIdColumn;
	@FXML
	private TableColumn<StudentProject, Double> markColumn;
	@FXML
	private TableColumn<StudentProject, String> classroomColumn;
	@FXML
	private TableColumn<StudentProject, String> studentEmailColumn;
	@FXML
	private TableColumn<StudentProject, Date> sendDateColumn;
	@FXML
	private TextField projectNameField;
	@FXML
	private DatePicker deadlineDatePicker;
	@FXML
	private TextField argumentsField;
	@FXML
	private Button expectedOutputButton;
	@FXML
	private Button studentListButton;
	@FXML
	private Button createProjectButton;
	private String currentUser;
	private String host;
	private int port;
	
	@Override
	public void initialize(final URL url, final ResourceBundle resourceBundle) {
		projectNameButton.setOnMouseClicked(event -> {
			projectNameButton.getItems().clear();
			projectNameButton.getItems().addAll(queryProjectNames());
		});
		projectNameButton.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(final ObservableValue<? extends String> observable, final String oldValue, final String newValue) {
				if (newValue != null) {
					updateTable();
				}
			}
		});
		
		studentNameColumn.setCellValueFactory(new PropertyValueFactory<StudentProject, String>("studentName"));
		studentIdColumn.setCellValueFactory(new PropertyValueFactory<StudentProject, String>("studentId"));
		studentEmailColumn.setCellValueFactory(new PropertyValueFactory<StudentProject, String>("studentEmail"));
		markColumn.setCellValueFactory(new PropertyValueFactory<StudentProject, Double>("mark"));
		classroomColumn.setCellValueFactory(new PropertyValueFactory<StudentProject, String>("classroom"));
		sendDateColumn.setCellValueFactory(new PropertyValueFactory<StudentProject, Date>("sendDate"));
		
		projectNameField.textProperty().addListener(event -> updateCreateProjectButton());
		deadlineDatePicker.setEditable(false);
		deadlineDatePicker.setOnAction(event -> updateCreateProjectButton());
		this.host = "localhost";
		this.port = 52112;
	}

	public void initUser(final WindowManager windowManager, final String login) {
		this.currentUser = login;
		logoutContextMenu.setOnAction(event -> {
			final Alert alert = new Alert(AlertType.CONFIRMATION);
			alert.setTitle("Logout");
			alert.setContentText("Are you sure you want to logout ?");
			alert.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> windowManager.showLoginView());
			
		});
		projectNameButton.setItems(FXCollections.observableList(queryProjectNames()));
	}
	
	private List<String> queryProjectNames() {
		final ArrayList<String> al = new ArrayList<>();
		String projectName;
		try {
			final ResultSet rs = MysqlRequest.getProjectNameByTeacher(currentUser);
			while (rs.next()) {
				projectName = rs.getString("intituleProjet");
				al.add(projectName);
			}
		} catch (final SQLException e) {
			e.printStackTrace();
			System.out.println(e.getSQLState());
		}
		return al.stream().distinct().collect(Collectors.toList());
	}
	
	public static final class StudentProject {
		private final SimpleStringProperty studentName;
		private final SimpleIntegerProperty studentId;
		private final SimpleStringProperty studentEmail;
		private final SimpleDoubleProperty mark;
		private final SimpleObjectProperty<Date> sendDate;
		private final SimpleStringProperty classroom;

		public StudentProject(final String studentName, final Integer studentId, final String studentEmail,final String classroom, final Double mark, final Date sendDate) {
			this.studentName = (studentName == null) ? new SimpleStringProperty() : new SimpleStringProperty(studentName);
			this.studentId = (studentId == null) ? new SimpleIntegerProperty() : new SimpleIntegerProperty(studentId);
			this.studentEmail = (studentEmail == null) ? new SimpleStringProperty() : new SimpleStringProperty(studentEmail);
			this.mark = (mark == null) ? new SimpleDoubleProperty() : new SimpleDoubleProperty(mark);
			this.sendDate = (sendDate == null) ? new SimpleObjectProperty<>() : new SimpleObjectProperty<>(sendDate);
			this.classroom = (classroom == null) ? new SimpleStringProperty() : new SimpleStringProperty(classroom);
		}
		
		public String getStudentName() {
			return studentName.get();
		}
		
		public int getStudentId() {
			return studentId.get();
		}

		public double getMark() {
			return mark.get();
		}

		public Date getSendDate() {
			return sendDate.get();
		}

		public String getClassroom() {
			return classroom.get();
		}
		public String getStudentEmail() {
			return studentEmail.get();
		}
	}

	private void updateTable() {
		try {
			studentProjectsTable.setItems(parseStudentProjectList());
			deleteProjectButton.setDisable(false);
		} catch (final MarkingDbManagementException e) {
			e.printStackTrace();
		}
	}

	private ObservableList<StudentProject> parseStudentProjectList()
	throws MarkingDbManagementException {
		final String intituleProjet = projectNameButton.getValue();
		final ArrayList<StudentProject> projets = new ArrayList<>();
		try {
			final ResultSet rs = MysqlRequest.getEvaluation(currentUser, intituleProjet);
			while (rs.next()) {
				final Date dateEnvoi = rs.getDate("EVALUATION_date_envoi");
				final String nom = rs.getString("nomEtu") + " " + rs.getString("prenomEtu");
				final String mail = rs.getString("emailEtu");
				final int numEtu = rs.getInt("numEtu");
				final BigDecimal note = Optional.ofNullable(rs.getBigDecimal("EVALUATION_note")).orElse(new BigDecimal(-1));
				final String classe = rs.getString("intituleClasse");
				projets.add(new StudentProject(nom, numEtu,mail , classe, note.doubleValue(), dateEnvoi));
			}
		} catch (final SQLException e) {
			System.out.println(e.getSQLState());
			throw new MarkingDbManagementException("Erreur lors de la récupération des données", e);
		}
		return FXCollections.observableArrayList(projets);
	}

	@FXML
	private void handleDeleteProjectAction() {
		//TODO Send delete request to DB + display response
	}

	@FXML
	private void handleSelectOutputAction() {
		final FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Select file");
		fileChooser.getExtensionFilters().add(new ExtensionFilter("Text (.txt)", "*.txt"));
		final File file = fileChooser.showOpenDialog(null);
		Optional.ofNullable(file).map(File::getPath).filter(path -> !path.isEmpty()).ifPresent(path -> {
			expectedOutputButton.setUserData(path);
			expectedOutputButton.setText(path);
		});
	}

	@FXML
	private void handleSelectListAction() {
		final FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Select file");
		fileChooser.getExtensionFilters().add(new ExtensionFilter("Comma Separated Values (.csv)", "*.csv"));
		final File file = fileChooser.showOpenDialog(null);
		Optional.ofNullable(file).map(File::getPath).filter(path -> !path.isEmpty()).ifPresent(path -> {
			studentListButton.setUserData(path);
			studentListButton.setText(path);
		});
		updateCreateProjectButton();
	}
	
	@FXML
	private void handleCreateProjectAction() {
		//TODO send to the server using a socket (scp temporarily)
		@SuppressWarnings("unused")
		final String expectedOutputPath = (String) expectedOutputButton.getUserData();

		try {
			//Create the project in db
			final Optional<String> projectId = insertProject();

			if (projectId.isPresent()) {
				try {
					//Parse the student list and send it to db
					if(sendOutputFileProjet(this.host, this.port, projectId.get())) {
						insertCSV(projectId.get());
						final Alert alert = new Alert(AlertType.INFORMATION);
						alert.setHeaderText("Création réussie");
						alert.setContentText("Le projet a été créé avec succès");
						alert.show();
					}
					
				} catch (final SQLException e) {
					e.printStackTrace();
					System.out.println(e.getSQLState());
					showWarning(SAVE_CSV_ERROR, "Une erreur serveur s'est produite lors de l'enregistrement du csv.");
				}
			}
		} catch (final SQLException e) {
			e.printStackTrace();
			System.out.println(e.getSQLState());
			showWarning(PROJECT_CREATION_ERROR, "Une erreur serveur s'est produite lors de la création du projet.");
		}
	}

	Thread.UncaughtExceptionHandler h = new Thread.UncaughtExceptionHandler() {
	    public void uncaughtException(Thread th, Throwable ex) {
	        System.out.println("Uncaught exception: " + ex);
	    }
	};
	
	private boolean sendOutputFileProjet(String serveur, int port ,String projName){
		final String expectedOutputPath = (String) expectedOutputButton.getUserData();
		File fichier = new File(expectedOutputPath);
		if(!fichier.exists()) {
			showWarning(PROJECT_CREATION_ERROR, "Le fichier passé en paramètre n'existe pas");
		}
		
		ExecutorService pool = Executors.newFixedThreadPool(15);
		Callable<Boolean> task= new SendOutputFileSocket(serveur, port, expectedOutputPath, projName);
		Future<Boolean> future = pool.submit(task);
		boolean bool = false;
		try {
			while(!future.isDone()) {
			}
			bool = future.get().booleanValue();
			if(!bool) {
				showWarning(SEND_OUTPUTFILE_ERROR, "erreur lors de la connexion au serveur ou de l'envoi");
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return bool;
		
	}

	private Optional<String> insertProject()
	throws SQLException {
		final String projectId = UUID.randomUUID().toString();
		final LocalDate deadline = deadlineDatePicker.getValue();
		if (deadline.compareTo(LocalDate.now().plusDays(3)) < 0) {
			showWarning(PROJECT_CREATION_ERROR, "L'échéance du projet doit être de 3 jours minimum.");
		} else {
			final String projectName = projectNameField.getText();
			if (MysqlRequest.getEvaluationByLoginProjName(currentUser, projectName).isBeforeFirst()) {
				showWarning(PROJECT_CREATION_ERROR, "Vous avez déjà créé un projet du même nom.");
			} else {
				final String arguments = argumentsField.getText();
				MysqlRequest.insertProject(projectId, deadline, projectName, arguments);
				return Optional.of(projectId);
			}
		}
		return Optional.empty();
	}

	private void insertCSV(final String projectId)
	throws SQLException {
		final StudentCsvParser sparser = new StudentCsvParser();
		sparser.parse((String) studentListButton.getUserData());
		final ArrayList<Student> students = sparser.getStudents();
		final ArrayList<Classroom> classrooms = sparser.getClassrooms();
		for(final Classroom classroom : classrooms) {
			if(!MysqlRequest.getIdPromotionRequest(classroom.getYear(), classroom.getClassroom()).isBeforeFirst()) {
				if(!MysqlRequest.getidClasseRequest(classroom.getClassroom()).isBeforeFirst())
				{
					MysqlRequest.insertClasse(classroom.getClassroom());
				}
				ResultSet rsClasse = MysqlRequest.getidClasseRequest(classroom.getClassroom());
				rsClasse.next();
				MysqlRequest.insertPromotion(classroom.getYear(), rsClasse.getInt("idClasse"));
			}
		}
			for (final Student student : students) {
				ResultSet rspromo = MysqlRequest.getIdPromotionRequest(student.getClassroom().getYear(), student.getClassroom().getClassroom());
				rspromo.next();
				int idPromotion = rspromo.getInt("idPromotion");
				if (!MysqlRequest.getStudentByNum(student.getNumEtu()).isBeforeFirst()) {
					
					MysqlRequest.insertStudent(student.getNumEtu(), student.getPrenom(), student.getNom(), student.getEmail() ,idPromotion);
				}
				MysqlRequest.insertEvaluation(projectId, currentUser, student.getNumEtu(), idPromotion);
			}
		}

	void showWarning(final String title, final String message) {
		final Alert alert = new Alert(AlertType.WARNING);
		alert.setHeaderText(title);
		alert.setContentText(message);
		alert.show();
	}

	private void updateCreateProjectButton() {
		createProjectButton.setDisable(projectNameField.getText().isEmpty() || deadlineDatePicker.getValue() == null || (String) studentListButton.getUserData() == null);
	}
}
