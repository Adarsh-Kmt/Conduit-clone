package kamathadarsh.Conduit.Service;

import jakarta.transaction.Transactional;
import kamathadarsh.Conduit.Exception.UserNotFoundException;

import kamathadarsh.Conduit.Response.*;
import kamathadarsh.Conduit.jooq.jooqGenerated.tables.pojos.UserTable;
import kamathadarsh.Conduit.jooqRepository.JOOQUserRepository;

import kamathadarsh.Conduit.Request.CreateUserRequest;
import kamathadarsh.Conduit.Request.UserUpdateRequest;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

@Service
@AllArgsConstructor
public class UserService {


    private final JOOQUserRepository jooqUserRepository;

    private static String IMAGE_DIR = "C:\\Users\\adaka\\OneDrive\\Desktop\\programming\\Springboot Projects\\Conduit-Medium-Clone\\Conduit\\src\\main\\resources\\static\\images";
    //private static String STOCK_PHOTO_IMAGE = "C:\\Users\\adaka\\OneDrive\\Desktop\\programming\\Springboot Projects\\Conduit-Medium-Clone\\Conduit\\src\\main\\resources\\static\\images\\";


    public CustomResponse followUser(String followerUsername, String toBeFollowedUsername){


        try{
            Optional<UserTable> userToBeFollowed = jooqUserRepository.findByUsername(toBeFollowedUsername);

            Optional<UserTable> userThatFollows = jooqUserRepository.findByUsername(followerUsername);

            if(!userToBeFollowed.isPresent()) throw new UserNotFoundException("user with Username " + toBeFollowedUsername + " not found.");
            if(!userThatFollows.isPresent()) throw new UserNotFoundException("user with Username " + followerUsername + " not found.");


            jooqUserRepository.followUser(followerUsername, toBeFollowedUsername);

            return new ProfileResponse(
                    toBeFollowedUsername,
                    userToBeFollowed.get().getBio(),
                    getProfilePicture(toBeFollowedUsername),
                    true
            );



        }
        catch (UserNotFoundException e) {

            return FailureResponse.builder()
                    .message(e.getMessage())
                    .status(HttpStatus.NOT_FOUND)
                    .build();


        }


    }

    public CustomResponse unfollowUser(String followerUsername, String toBeUnfollowedUsername) {


        try{

            Optional<UserTable> followerUserExists = jooqUserRepository.findByUsername(followerUsername);
            Optional<UserTable> userToBeFollowedExists = jooqUserRepository.findByUsername(toBeUnfollowedUsername);

            if(!followerUserExists.isPresent()) throw new UserNotFoundException("user with id " + followerUsername + " not found");
            if(!userToBeFollowedExists.isPresent()) throw new UserNotFoundException("user with id " + toBeUnfollowedUsername + " not found");

            UserTable userToBeFollowed = userToBeFollowedExists.get();


            if(!jooqUserRepository.checkIfUserFollowsAnotherUser(followerUsername, toBeUnfollowedUsername)){

                return FailureResponse.builder()
                        .status(HttpStatus.NOT_FOUND)
                        .message("you didn't follow the user in the first place.")
                        .build();
            }

            jooqUserRepository.unfollowUser(followerUsername, toBeUnfollowedUsername);

            return new ProfileResponse(
                    toBeUnfollowedUsername,
                    userToBeFollowed.getBio(),
                    getProfilePicture(toBeUnfollowedUsername),
                    false
            );
        }
        catch(UserNotFoundException e){

            return FailureResponse.builder()
                    .message(e.getMessage())
                    .status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }

    public CustomResponse getProfile(String username, String currUserUsername) {

        try{

            Optional<UserTable> user = jooqUserRepository.findByUsername(username);

            Optional<UserTable> currUser = jooqUserRepository.findByUsername(currUserUsername);

            if(!user.isPresent()) throw new UserNotFoundException("user with username " + username + " not found.");
            if(!currUser.isPresent()) throw new UserNotFoundException("user with username " + currUserUsername + " not found.");

            boolean isFollowing = jooqUserRepository.checkIfUserFollowsAnotherUser(currUserUsername, username);

            return new ProfileResponse(
                    username,
                    user.get().getBio(),
                    getProfilePicture(username),
                    isFollowing

            );

        }
        catch(UserNotFoundException e){

            return FailureResponse.builder()
                    .message(e.getMessage())
                    .status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }

    public CustomResponse userUpdate(String currUserUsername, UserUpdateRequest userUpdateRequest){



        try{


            Optional<UserTable> currUserExists = jooqUserRepository.findByUsername(currUserUsername);

            if(!currUserExists.isPresent()) throw new UserNotFoundException("user with username " + currUserUsername + " not found");

            String finalUsername = currUserUsername;

            jooqUserRepository.updateUser(currUserUsername, userUpdateRequest);


            UserTable updatedUser = jooqUserRepository.findByUsername(finalUsername).get();

            return UserResponse.builder()
                    .bio(updatedUser.getBio())
                    .image(getProfilePicture(updatedUser.getUsername()))
                    .email(updatedUser.getEmailId())
                    .username(updatedUser.getUsername())
                    .build();


        }

        catch(UserNotFoundException e){

            return FailureResponse.builder()
                    .message(e.getMessage())
                    .status(HttpStatus.NOT_FOUND)
                    .build();

        }

    }

    public CustomResponse saveProfilePicture(MultipartFile image, String username){

        try {
            InputStream inputStream = image.getInputStream();
            byte[] data = new byte[inputStream.available()];
            inputStream.read(data);
            String imageLocation = IMAGE_DIR+"\\"+username;
            FileOutputStream fileOutputStream = new FileOutputStream(imageLocation);
            fileOutputStream.write(data);

            fileOutputStream.flush();
            fileOutputStream.close();

            //Files.copy(inputStream, Paths.get(imageLocation), StandardCopyOption.REPLACE_EXISTING);


            return new SuccessResponse(imageLocation);
        }

        catch (IOException e) {

            return FailureResponse.builder()
                    .message("could not upload profile picture.")
                    .status(HttpStatus.NOT_FOUND)
                    .build();
        }

    }

    public byte[] getProfilePicture(String username){


        try {
            boolean fileExists = Files.exists(Paths.get(IMAGE_DIR+"\\"+username));

            if(!fileExists){
                username = "blankProfilePicture";
            }
            byte[] data = Files.readAllBytes(Paths.get(IMAGE_DIR+"\\"+username));
            System.out.println((Paths.get(IMAGE_DIR+"\\"+username)).getFileName().toString());
            return data;

        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    @Transactional
    public CustomResponse createUser(CreateUserRequest createUserRequest, MultipartFile profilePicture){


        CustomResponse responseToSaveProfilePictureRequest
                = saveProfilePicture(profilePicture, createUserRequest.getUsername());

        if(responseToSaveProfilePictureRequest instanceof FailureResponse) return responseToSaveProfilePictureRequest;

        SuccessResponse successResponse = (SuccessResponse)responseToSaveProfilePictureRequest;
        String imageLocation = successResponse.getSuccessMessage();

        UserTable user = new UserTable(
                createUserRequest.getUsername(),
                createUserRequest.getBio(),
                createUserRequest.getEmailId(),
                imageLocation,
                createUserRequest.getPassword()
        );
        jooqUserRepository.createUser(user);

        return UserResponse.builder()
                .username(user.getUsername())
                .bio(user.getBio())
                .email(user.getEmailId())
                .build();

    }


}
